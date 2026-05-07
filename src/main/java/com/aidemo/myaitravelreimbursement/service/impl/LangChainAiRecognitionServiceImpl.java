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
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;

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
            File imageFile = new File(fullPath);
            if (!imageFile.exists()) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
            }

            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = detectMimeType(imageFile.getName());
            String imageDataUri = "data:" + mimeType + ";base64," + base64Image;

            String prompt = "invoice".equals(type)
                    ? RecognitionPrompts.INVOICE_PROMPT
                    : RecognitionPrompts.SCREENSHOT_PROMPT;

            String aiResponse = callAiApi(imageDataUri, prompt);

            Object parsedResult = parseStructuredResult(aiResponse, type);
            RecognitionResult rr = mapToEntity(parsedResult, file, type, aiResponse);

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
            log.error("LangChain4j AI识别失败: fileId={}", fileId, e);
            file.setStatus(FileStatus.FAILED);
            uploadFileMapper.updateById(file);
            throw new BusinessException(ErrorCode.AI_RECOGNITION_FAILED, "AI识别失败: " + e.getMessage());
        }
    }

    @Override
    public RecognitionResultVO recognizeFromFile(File file, String type) {
        throw new UnsupportedOperationException("请使用 recognize(Long fileId, String type) 方法");
    }

    /**
     * 通过 LangChain4j ChatModel 调用视觉大模型
     */
    private String callAiApi(String imageDataUri, String prompt) {
        ImageContent imageContent = ImageContent.from(imageDataUri);
        UserMessage userMessage = UserMessage.from(prompt, imageContent);

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(userMessage))
                .modelName(aiProperties.getModel())
                .build();

        dev.langchain4j.model.chat.response.ChatResponse response = chatModel.chat(request);

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
            if ("invoice".equals(type)) {
                return objectMapper.readValue(aiResponse, InvoiceRecognitionResult.class);
            } else {
                return objectMapper.readValue(aiResponse, ScreenshotRecognitionResult.class);
            }
        } catch (Exception e) {
            log.error("解析AI响应失败: {}", aiResponse, e);
            throw new BusinessException(ErrorCode.AI_RECOGNITION_FAILED,
                    "AI 返回格式解析失败: " + e.getMessage());
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
        rr.setConfidence(new BigDecimal("0.9000"));

        if ("invoice".equals(type) && parsedResult instanceof InvoiceRecognitionResult invoice) {
            rr.setExpenseType(invoice.getExpenseType());
            rr.setInvoiceNumber(invoice.getInvoiceNumber());
            rr.setInvoiceDate(invoice.getInvoiceDate());
            rr.setTotalAmount(invoice.getTotalAmount());
            rr.setSeller(invoice.getSeller());
            rr.setBuyer(invoice.getBuyer());
            rr.setDescription(invoice.getDescription());
            rr.setAiFilename(invoice.getRewriteFileNameByAi());
        } else if (parsedResult instanceof ScreenshotRecognitionResult screenshot) {
            rr.setExpenseType(screenshot.getExpenseType());
            rr.setConsumptionDate(screenshot.getConsumptionDate());
            rr.setTotalConsumption(screenshot.getTotalConsumption());
            rr.setConsumptionCount(screenshot.getConsumptionCount());
            rr.setDescription(screenshot.getDescription());
            rr.setAiFilename(screenshot.getRewriteFileNameByAi());
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
}
