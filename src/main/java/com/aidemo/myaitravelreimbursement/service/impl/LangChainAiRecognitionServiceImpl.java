package com.aidemo.myaitravelreimbursement.service.impl;

import com.aidemo.myaitravelreimbursement.ai.RecognitionPrompts;
import com.aidemo.myaitravelreimbursement.common.BusinessException;
import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.config.AiProperties;
import com.aidemo.myaitravelreimbursement.config.StorageConfig;
import com.aidemo.myaitravelreimbursement.constant.FileStatus;
import com.aidemo.myaitravelreimbursement.dto.ai.InvoiceRecognitionResult;
import com.aidemo.myaitravelreimbursement.dto.ai.ScreenshotRecognitionResult;
import com.aidemo.myaitravelreimbursement.dto.response.RecognitionResultVO;
import com.aidemo.myaitravelreimbursement.entity.RecognitionResult;
import com.aidemo.myaitravelreimbursement.entity.UploadFile;
import com.aidemo.myaitravelreimbursement.mapper.RecognitionResultMapper;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.AiRecognitionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Iterator;
import java.util.UUID;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;


/**
 * 基于 LangChain4j 的 AI 识别服务实现
 * <p>
 * 通过 {@code ai.provider=langchain4j} 配置启用（默认）。
 * 使用 {@link ChatModel} 的 OpenAI-compatible API，支持 DashScope / Moonshot / Kimi 等视觉大模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.provider", havingValue = "langchain4j", matchIfMissing = true)
public class LangChainAiRecognitionServiceImpl implements AiRecognitionService {

    private final UploadFileMapper uploadFileMapper;
    private final RecognitionResultMapper recognitionResultMapper;
    private final AiProperties aiProperties;
    private final StorageConfig storageConfig;
    private final ObjectMapper objectMapper;
    private final ChatModel chatModel;

    /**
     * 独立于当前事务更新文件识别状态及重命名信息。
     * 解决 @Transactional 异常回滚时覆盖 status 的问题。
     */
    private void updateFileIndependently(Long fileId, int status, String name, String storagePath) {
        LambdaUpdateWrapper<UploadFile> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UploadFile::getId, fileId);
        if (status > 0) {
            wrapper.set(UploadFile::getStatus, status);
        }
        if (name != null) {
            wrapper.set(UploadFile::getName, name);
        }
        if (storagePath != null) {
            wrapper.set(UploadFile::getStoragePath, storagePath);
        }
        uploadFileMapper.update(null, wrapper);
    }

    @Override
    //@Transactional(noRollbackFor = Exception.class)
    public RecognitionResultVO recognize(Long fileId, String type) {
        UploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
        }

        file.setStatus(FileStatus.RECOGNIZING);
        uploadFileMapper.updateById(file);

        try {
            String fullPath = storageConfig.getBasePath() + "/" + file.getStoragePath();
            File imageFile = new File(fullPath);
            if (!imageFile.exists()) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
            }

            byte[] imageBytes;
            String mimeType;
            if ("application/pdf".equals(detectMimeType(imageFile.getName()))) {
                imageBytes = renderPdfFirstPageToImage(fullPath);
                mimeType = "image/jpeg";
            } else {
                byte[] rawBytes = Files.readAllBytes(imageFile.toPath());
                imageBytes = compressImage(rawBytes, detectMimeType(imageFile.getName()));
                mimeType = "image/jpeg";
            }
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
          
            String imageDataUri = "data:" + mimeType + ";base64," + base64Image;

            String prompt = "invoice".equals(type)
                    ? RecognitionPrompts.INVOICE_PROMPT
                    : RecognitionPrompts.SCREENSHOT_PROMPT;

            String Type = "invoice".equals(type) ? "发票" : "截图";

            String aiResponse = callAiApi(imageDataUri, prompt,Type);

            Object parsedResult = parseStructuredResult(aiResponse, type);
            RecognitionResult rr = mapToEntity(parsedResult, file, type, aiResponse);

            // 置信度低于 0.5 视为识别失败
            if (rr.getConfidence() != null && rr.getConfidence().compareTo(new BigDecimal("0.5")) < 0) {
                updateFileIndependently(fileId, FileStatus.FAILED, null, null);
                throw new RuntimeException("识别置信度过低(=" + rr.getConfidence() + ")，请检查图片清晰度后重试");
            }

            // 先重命名物理文件，再用更新后的数据入库（仅发票/截图，附件跳过）
            String aiFilename = rr.getAiFilename();
            String newFileName = null;
            String newStoragePath = null;
            if (aiFilename != null && !aiFilename.isBlank() && !"attachment".equals(type)) {
                String originalExt = getFileExtension(file.getOriginalName());
                String uniqueId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                newFileName = aiFilename + "_" + uniqueId + originalExt;
                Path oldPath = Paths.get(fullPath);
                Path newPath = oldPath.resolveSibling(newFileName);
                Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                String oldStoragePath = file.getStoragePath();
                newStoragePath = oldStoragePath.substring(0, oldStoragePath.lastIndexOf('/') + 1) + newFileName;
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

            // 入库：UploadFile（强制写入 SUCCESS 状态及重命名信息，防止事务内 UPDATE 未执行）
            updateFileIndependently(fileId, FileStatus.SUCCESS, newFileName, newStoragePath);

            return RecognitionResultVO.fromEntity(rr);
        } catch (Exception e) {
            log.error("LangChain4j AI识别失败: fileId={}", fileId, e);
            updateFileIndependently(fileId, FileStatus.FAILED, null, null);
            throw new RuntimeException("AI识别失败: " + e.getMessage(), e);
        }
    }

    @Override
    public RecognitionResultVO recognizeFromFile(File file, String type) {
        throw new UnsupportedOperationException("请使用 recognize(Long fileId, String type) 方法");
    }

    /**
     * 通过 LangChain4j ChatModel 调用视觉大模型
     */
    private String callAiApi(String imageDataUri, String prompt, String Type) {
        UserMessage userMessage = UserMessage.from(
                TextContent.from(prompt),
                ImageContent.from(imageDataUri)
        );

        ResponseFormat invoiceResponseFormat = ResponseFormat.builder()
                .type(JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("InvoiceRecognitionResult")
                        .rootElement(JsonObjectSchema.builder()
                                .addStringProperty("expense_type", "费用类型，枚举：transport / catering / accommodation / purchase")
                                .addStringProperty("invoice_number", "发票号码")
                                .addStringProperty("invoice_date", "开票日期，格式：YYYY-MM-DD")
                                .addNumberProperty("total_amount", "价税合计金额")
                                .addStringProperty("seller", "销售方名称")
                                .addStringProperty("buyer", "购买方名称")
                                .addStringProperty("description", "文件简述")
                                .addStringProperty("rewriteFileNameByAi", "AI生成的中文文件名，如'滴滴出行客运服务费_20260405'，不含后缀")
                                .addNumberProperty("confidence", "置信度，0到1之间的小数")
                                .required("expense_type")
                                .build())
                        .build())
                .build();

        ResponseFormat screenshotResponseFormat = ResponseFormat.builder()
                .type(JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("ScreenshotRecognitionResult")
                        .rootElement(JsonObjectSchema.builder()
                                .addStringProperty("expense_type", "费用类型，枚举：transport / catering / accommodation / purchase")
                                .addStringProperty("consumption_date", "消费日期，格式：YYYY-MM-DD")
                                .addNumberProperty("total_consumption", "消费总额")
                                .addIntegerProperty("consumption_count", "消费次数")
                                .addStringProperty("description", "文件简述")
                                .addStringProperty("rewriteFileNameByAi", "AI生成的中文文件名，如'微信支付账单总额_20260405'，不含后缀")
                                .addNumberProperty("confidence", "置信度，0到1之间的小数")
                                .required("expense_type")
                                .build())
                        .build()).build();

        ChatRequest chatRequest = ChatRequest.builder()
                .responseFormat(Type.equals("invoice") ? invoiceResponseFormat : screenshotResponseFormat)
                .messages(userMessage)
                .build();

        ChatResponse response = chatModel.chat(chatRequest);

        if (response == null || response.aiMessage() == null) {
            throw new BusinessException(ErrorCode.AI_RECOGNITION_FAILED, "AI 返回为空");
        }

        String text = response.aiMessage().text();
        log.debug("AI 原始响应: {}", text);
        return text;
    }

    /**
     * 将 AI 返回的 JSON 解析为结构化 POJO
     */
    private Object parseStructuredResult(String aiResponse, String type) {
        try {
            // AI 可能将 null 日期写成字符串 "null"，预处理后替换为真正的 JSON null
            String preprocessed = aiResponse.replaceAll(
                    "\"(" + ("invoice".equals(type) ? "invoice_date" : "consumption_date") + ")\"\\s*:\\s*\"null\"",
                    "\"$1\": null"
            );
            if ("invoice".equals(type)) {
                return objectMapper.readValue(preprocessed, InvoiceRecognitionResult.class);
            } else {
                return objectMapper.readValue(preprocessed, ScreenshotRecognitionResult.class);
            }
        } catch (Exception e) {
            log.error("解析AI响应失败: {}", aiResponse, e);
            // 直接抛 RuntimeException 而非 BusinessException，避免被外层 catch 再次处理并触发事务回滚
            throw new RuntimeException("AI 返回格式解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将结构化 POJO 映射为数据库实体
     */
    private RecognitionResult mapToEntity(Object parsedResult, UploadFile file, String type, String rawResponse) {
        RecognitionResult rr = new RecognitionResult();
        rr.setProjectId(file.getProjectId());
        rr.setFileId(file.getId());
        rr.setType(type);
        rr.setRawResponse(rawResponse);

        BigDecimal aiConfidence = null;
        if ("invoice".equals(type) && parsedResult instanceof InvoiceRecognitionResult invoice) {
            rr.setExpenseType(invoice.getExpenseType());
            rr.setInvoiceNumber(invoice.getInvoiceNumber());
            rr.setInvoiceDate(invoice.getInvoiceDate());
            rr.setTotalAmount(invoice.getTotalAmount());
            rr.setSeller(invoice.getSeller());
            rr.setBuyer(invoice.getBuyer());
            rr.setDescription(invoice.getDescription());
            rr.setAiFilename(invoice.getRewriteFileNameByAi());
            aiConfidence = invoice.getConfidence();
        } else if (parsedResult instanceof ScreenshotRecognitionResult screenshot) {
            rr.setExpenseType(screenshot.getExpenseType());
            rr.setConsumptionDate(screenshot.getConsumptionDate());
            rr.setTotalConsumption(screenshot.getTotalConsumption());
            rr.setConsumptionCount(screenshot.getConsumptionCount());
            rr.setDescription(screenshot.getDescription());
            rr.setAiFilename(screenshot.getRewriteFileNameByAi());
            aiConfidence = screenshot.getConfidence();
        }

        if (aiConfidence != null && aiConfidence.compareTo(BigDecimal.ZERO) > 0) {
            rr.setConfidence(aiConfidence);
        } else {
            rr.setConfidence(new BigDecimal("0"));
        }

        return rr;
    }

    /**
     * 根据文件名后缀检测 MIME 类型
     */
    private String detectMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".heic")) return "image/heic";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "image/jpeg";
    }

    /**
     * 将 PDF 第一页渲染为 JPEG 图片字节数组，供 AI 视觉模型识别
     */
    private byte[] renderPdfFirstPageToImage(String pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(new File(pdfPath))) {
            if (document.getNumberOfPages() == 0) {
                throw new BusinessException(ErrorCode.AI_RECOGNITION_FAILED, "PDF 文件为空，无可识别页面");
            }
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(0, 150);
            return compressImage(toJpegBytes(image), null);
        }
    }

    /**
     * 对图片进行压缩：缩放到最大 1024x1024 像素，然后 JPEG 质量压缩到 80%
     *
     * @param image 原始图片字节数组
     * @return 压缩后的 JPEG 字节数组
     */
    private byte[] toJpegBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    private byte[] compressImage(byte[] rawBytes, String mimeType) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(rawBytes));
        if (original == null) {
            throw new BusinessException(ErrorCode.AI_RECOGNITION_FAILED, "无法解析图片格式");
        }

        int width = original.getWidth();
        int height = original.getHeight();
        int maxDimension = 1600;
        int targetSizeBytes = 300 * 1024; // 200KB

        // 如果原图尺寸和大小都满足要求，直接返回
        if (width <= maxDimension && height <= maxDimension && rawBytes.length <= targetSizeBytes && "image/jpeg".equals(mimeType)) {
            return rawBytes;
        }

        // 计算缩放比例，保持宽高比
        double scale = Math.min(1.0, (double) maxDimension / Math.max(width, height));
        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        // 二分搜索最优压缩质量，控制在 targetSizeBytes 以内
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
                lo = quality;          // 可以接受更高质量
                quality = (lo + hi) / 2;
            } else {
                hi = quality;          // 需要更低质量
                quality = (lo + hi) / 2;
            }
        }

        if (best == null) {
            // 兜底：最低质量仍超限，强制定为最小尺寸
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
