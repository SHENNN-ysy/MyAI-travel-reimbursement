package com.aidemo.myaitravelreimbursement.service.impl;

import com.aidemo.myaitravelreimbursement.ai.RecognitionPrompts;
import com.aidemo.myaitravelreimbursement.common.BusinessException;
import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.config.AiProperties;
import com.aidemo.myaitravelreimbursement.config.StorageConfig;
import com.aidemo.myaitravelreimbursement.constant.FileStatus;
import com.aidemo.myaitravelreimbursement.dto.response.RecognitionResultVO;
import com.aidemo.myaitravelreimbursement.entity.RecognitionResult;
import com.aidemo.myaitravelreimbursement.entity.UploadFile;
import com.aidemo.myaitravelreimbursement.mapper.RecognitionResultMapper;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.AiRecognitionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

/**
 * AI 识别服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.provider", havingValue = "okhttp")
public class AiRecognitionServiceImpl implements AiRecognitionService {

    private final UploadFileMapper uploadFileMapper;
    private final RecognitionResultMapper recognitionResultMapper;
    private final AiProperties aiProperties;
    private final StorageConfig storageConfig;
    private final ObjectMapper objectMapper;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String SUPPORTED_IMAGE_MIME_PREFIX = "image/";
    private static final String PDF_MIME_TYPE = "application/pdf";

    /**
     * 独立于当前事务更新文件识别状态。
     * 解决 @Transactional 异常回滚时覆盖 status 的问题。
     */
    private void updateFileStatusIndependently(Long fileId, int status) {
        LambdaUpdateWrapper<UploadFile> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UploadFile::getId, fileId).set(UploadFile::getStatus, status);
        uploadFileMapper.update(null, wrapper);
    }

    @Override
    @Transactional(noRollbackFor = Exception.class)
    public RecognitionResultVO recognize(Long fileId, String type) {
        UploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
        }

        file.setStatus(FileStatus.RECOGNIZING);
        uploadFileMapper.updateById(file);

        try {
            String fullPath = storageConfig.getBasePath() + "/" + file.getStoragePath();
            if (!new File(fullPath).exists()) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
            }

            String mimeType = file.getMimeType();
            byte[] fileBytes;

            if (PDF_MIME_TYPE.equals(mimeType)) {
                // PDF: 将第一页渲染为 PNG
                fileBytes = renderPdfFirstPageToImage(fullPath);
                mimeType = "image/jpeg";
            } else if (mimeType == null || !mimeType.startsWith(SUPPORTED_IMAGE_MIME_PREFIX)) {
                throw new BusinessException(ErrorCode.AI_RECOGNITION_FAILED,
                        "不支持的文件类型，仅支持图片格式（PNG、JPG、GIF、WEBP、BMP）和 PDF");
            } else {
                // 普通图片文件：读取后压缩到 200KB 以内
                fileBytes = Files.readAllBytes(new File(fullPath).toPath());
                fileBytes = compressImage(fileBytes);
            }

            String base64Image = Base64.getEncoder().encodeToString(fileBytes);
            String prompt = "invoice".equals(type) ? RecognitionPrompts.INVOICE_PROMPT : RecognitionPrompts.SCREENSHOT_PROMPT;

            JsonNode result = callAiApi(base64Image, mimeType, prompt);

            RecognitionResult rr = parseRecognitionResult(result, file, type);

            // 置信度低于 0.5 视为识别失败
            if (rr.getConfidence() != null && rr.getConfidence().compareTo(new BigDecimal("0.5")) < 0) {
                updateFileStatusIndependently(fileId, FileStatus.FAILED);
                throw new RuntimeException("识别置信度过低(=" + rr.getConfidence() + ")，请检查图片清晰度后重试");
            }

            // 先重命名物理文件，再用更新后的数据入库（仅发票/截图，附件跳过）
            String aiFilename = rr.getAiFilename();
            if (aiFilename != null && !aiFilename.isBlank() && !"attachment".equals(type)) {
                String originalExt = getFileExtension(file.getOriginalName());
                String newFileName = aiFilename + originalExt;
                Path oldPath = Paths.get(fullPath);
                Path newPath = oldPath.resolveSibling(newFileName);
                Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                file.setName(newFileName);
                String oldStoragePath = file.getStoragePath();
                String newStoragePath = oldStoragePath.substring(0, oldStoragePath.lastIndexOf('/') + 1) + newFileName;
                file.setStoragePath(newStoragePath);
                log.info("文件重命名完成: {} -> {}", oldStoragePath, newStoragePath);
            }

            // 入库：RecognitionResult
            LambdaQueryWrapper<RecognitionResult> queryWrapper = new LambdaQueryWrapper<RecognitionResult>()
                    .eq(RecognitionResult::getFileId, fileId);
            RecognitionResult existing = recognitionResultMapper.selectOne(queryWrapper);
            if (existing != null) {
                rr.setId(existing.getId());
                recognitionResultMapper.updateById(rr);
            } else {
                recognitionResultMapper.insert(rr);
            }

            // 入库：UploadFile（强制写入 SUCCESS 状态，防止事务内 UPDATE 未执行）
            updateFileStatusIndependently(fileId, FileStatus.SUCCESS);

            return RecognitionResultVO.fromEntity(rr);
        } catch (Exception e) {
            log.error("AI识别失败: fileId={}", fileId, e);
            updateFileStatusIndependently(fileId, FileStatus.FAILED);
            throw new RuntimeException("AI识别失败: " + e.getMessage(), e);
        }
    }

    @Override
    public RecognitionResultVO recognizeFromFile(File file, String type) {
        throw new UnsupportedOperationException("请使用 recognize(Long fileId, String type) 方法");
    }


    private JsonNode callAiApi(String base64Image, String mimeType, String prompt) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(aiProperties.getTimeout(), TimeUnit.SECONDS)
                .writeTimeout(aiProperties.getTimeout(), TimeUnit.SECONDS)
                .readTimeout(aiProperties.getTimeout(), TimeUnit.SECONDS)
                .build();

        String dataUri = "data:" + mimeType + ";base64," + base64Image;

        Map<String, Object> imageUrlObj = Map.of("url", dataUri);
        Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
        Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", imageUrlObj);
        Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", new Object[]{textContent, imageContent}
        );
        Map<String, Object> requestBody = Map.of(
                "model", aiProperties.getModel(),
                "messages", new Object[]{userMessage}
        );

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        log.debug("AI request body: {}", jsonBody);

        Request request = new Request.Builder()
                .url(aiProperties.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + aiProperties.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("AI API 调用失败: HTTP " + response.code() + ", body: " + errorBody);
            }
            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();
            return objectMapper.readTree(content);
        }
    }

    private RecognitionResult parseRecognitionResult(JsonNode json, UploadFile file, String type) {
        RecognitionResult rr = new RecognitionResult();
        rr.setProjectId(file.getProjectId());
        rr.setFileId(file.getId());
        rr.setType(type);
        rr.setRawResponse(json.toString());

        if ("invoice".equals(type)) {
            rr.setExpenseType(json.path("expense_type").asText(null));
            rr.setInvoiceNumber(json.path("invoice_number").asText(null));
            String dateStr = json.path("invoice_date").asText(null);
            if (dateStr != null && !dateStr.isEmpty()) {
                rr.setInvoiceDate(LocalDate.parse(dateStr));
            }
            rr.setTotalAmount(new BigDecimal(json.path("total_amount").asText("0")));
            rr.setSeller(json.path("seller").asText(null));
            rr.setBuyer(json.path("buyer").asText(null));
            rr.setDescription(json.path("description").asText(null));
            rr.setAiFilename(json.path("rewriteFileNameByAi").asText(null));
        } else {
            rr.setExpenseType(json.path("expense_type").asText(null));
            String dateStr = json.path("consumption_date").asText(null);
            if (dateStr != null && !dateStr.isEmpty()) {
                rr.setConsumptionDate(LocalDate.parse(dateStr));
            }
            rr.setTotalConsumption(new BigDecimal(json.path("total_consumption").asText("0")));
            rr.setConsumptionCount(json.path("consumption_count").asText(null));
            rr.setDescription(json.path("description").asText(null));
            rr.setAiFilename(json.path("rewriteFileNameByAi").asText(null));
        }

        rr.setConfidence(json.path("confidence").isNumber()
                ? new BigDecimal(json.path("confidence").asText())
                : new BigDecimal("0.9000"));
        return rr;
    }

    /**
     * 将 PDF 第一页渲染为 JPEG 格式字节数组
     *
     * @param rawBytes PDF 文件完整路径
     * @return JPEG 图片字节数组
     */
    private byte[] compressImage(byte[] rawBytes) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(rawBytes));
        if (original == null) {
            throw new BusinessException(ErrorCode.AI_RECOGNITION_FAILED, "无法解析图片格式");
        }

        int width = original.getWidth();
        int height = original.getHeight();
        int maxDimension = 1600;
        int targetSizeBytes = 200 * 1024; // 200KB

        if (width <= maxDimension && height <= maxDimension && rawBytes.length <= targetSizeBytes) {
            return rawBytes;
        }

        double scale = Math.min(1.0, (double) maxDimension / Math.max(width, height));
        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        float lo = 0.05f, hi = 1.0f, quality = 0.80f;
        byte[] best = null;

        for (int i = 0; i < 8; i++) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(ImageIO.createImageOutputStream(baos));
            writer.write(null, new IIOImage(resized, null, null), param);
            writer.dispose();

            byte[] result = baos.toByteArray();
            if (result.length <= targetSizeBytes) {
                best = result;
                lo = quality;
                quality = (lo + hi) / 2;
            } else {
                hi = quality;
                quality = (lo + hi) / 2;
            }
        }

        if (best == null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.05f);
            writer.setOutput(ImageIO.createImageOutputStream(baos));
            writer.write(null, new IIOImage(resized, null, null), param);
            writer.dispose();
            best = baos.toByteArray();
        }

        int originalSizeKB = rawBytes.length / 1024;
        int compressedSizeKB = best.length / 1024;
        log.info("图片压缩完成: {}x{} -> {}x{}, 大小: {}KB -> {}KB",
                width, height, newWidth, newHeight, originalSizeKB, compressedSizeKB);
        return best;
    }

    private byte[] renderPdfFirstPageToImage(String pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(new File(pdfPath))) {
            if (document.getNumberOfPages() == 0) {
                throw new BusinessException(ErrorCode.AI_RECOGNITION_FAILED, "PDF 文件为空，无可识别页面");
            }
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(0, 150);
            return compressImage(toJpegBytes(image));
        }
    }

    private byte[] toJpegBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    /**
     * 从文件名中提取后缀（含点号），如 ".jpg"
     */
    private String getFileExtension(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf("."));
    }
}
