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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;

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

    @Override
    @Transactional
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
                // 普通图片文件
                fileBytes = Files.readAllBytes(new File(fullPath).toPath());
            }

            String base64Image = Base64.getEncoder().encodeToString(fileBytes);
            String prompt = "invoice".equals(type) ? RecognitionPrompts.INVOICE_PROMPT : RecognitionPrompts.SCREENSHOT_PROMPT;

            JsonNode result = callAiApi(base64Image, mimeType, prompt);

            RecognitionResult rr = parseRecognitionResult(result, file, type);

            // 根据 fileId 查询是否存在记录，存在则更新，不存在则插入
            LambdaQueryWrapper<RecognitionResult> queryWrapper = new LambdaQueryWrapper<RecognitionResult>()
                    .eq(RecognitionResult::getFileId, fileId);
            RecognitionResult existing = recognitionResultMapper.selectOne(queryWrapper);
            if (existing != null) {
                rr.setId(existing.getId());
                recognitionResultMapper.updateById(rr);
            } else {
                recognitionResultMapper.insert(rr);
            }

            file.setStatus(FileStatus.SUCCESS);
            uploadFileMapper.updateById(file);

            return RecognitionResultVO.fromEntity(rr);
        } catch (BusinessException e) {
            file.setStatus(FileStatus.FAILED);
            uploadFileMapper.updateById(file);
            throw e;
        } catch (Exception e) {
            log.error("AI识别失败: fileId={}", fileId, e);
            file.setStatus(FileStatus.FAILED);
            uploadFileMapper.updateById(file);
            throw new BusinessException(ErrorCode.AI_RECOGNITION_FAILED, "AI识别失败: " + e.getMessage());
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
        } else {
            rr.setExpenseType(json.path("expense_type").asText(null));
            String dateStr = json.path("consumption_date").asText(null);
            if (dateStr != null && !dateStr.isEmpty()) {
                rr.setConsumptionDate(LocalDate.parse(dateStr));
            }
            rr.setTotalConsumption(new BigDecimal(json.path("total_consumption").asText("0")));
            rr.setConsumptionCount(json.path("consumption_count").asText(null));
            rr.setDescription(json.path("description").asText(null));
        }

        rr.setConfidence(new BigDecimal("0.9000"));
        return rr;
    }

    /**
     * 将 PDF 第一页渲染为 JPEG 格式字节数组
     *
     * @param pdfPath PDF 文件完整路径
     * @return JPEG 图片字节数组
     */
    private byte[] renderPdfFirstPageToImage(String pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(new File(pdfPath))) {
            if (document.getNumberOfPages() == 0) {
                throw new BusinessException(ErrorCode.AI_RECOGNITION_FAILED, "PDF 文件为空，无可识别页面");
            }
            PDFRenderer renderer = new PDFRenderer(document);
            // 第一页，96 DPI，JPEG 压缩，兼顾清晰度与请求体大小
            BufferedImage image = renderer.renderImageWithDPI(0, 96);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(image, "JPG", baos);
                return baos.toByteArray();
            }
        }
    }
}
