package com.aidemo.myaitravelreimbursement.service.impl;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

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

            String base64Image = Base64.getEncoder().encodeToString(Files.readAllBytes(imageFile.toPath()));
            String prompt = buildPrompt(type);

            JsonNode result = callAiApi(base64Image, prompt);

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

    private String buildPrompt(String type) {
        if ("invoice".equals(type)) {
            return """
                请识别这张发票图片，提取以下信息并以JSON格式返回：
                {
                    "expense_type": "费用类型(transport/catering/accommodation/purchase)",
                    "invoice_number": "发票号码",
                    "invoice_date": "开票日期(格式: YYYY-MM-DD)",
                    "total_amount": "价税合计金额(数字)",
                    "seller": "销售方名称",
                    "buyer": "购买方名称",
                    "description": "文件简述"
                }
                如果无法识别某字段，请返回null。请只返回JSON，不要添加任何解释。
                """;
        } else {
            return """
                请识别这张截图图片，提取以下信息并以JSON格式返回：
                {
                    "expense_type": "费用类型(transport/catering/accommodation/purchase)",
                    "consumption_date": "消费日期(格式: YYYY-MM-DD)",
                    "total_consumption": "消费总额(数字)",
                    "consumption_count": "消费次数",
                    "description": "文件简述"
                }
                如果无法识别某字段，请返回null。请只返回JSON，不要添加任何解释。
                """;
        }
    }

    private JsonNode callAiApi(String base64Image, String prompt) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(aiProperties.getTimeout(), TimeUnit.SECONDS)
                .writeTimeout(aiProperties.getTimeout(), TimeUnit.SECONDS)
                .readTimeout(aiProperties.getTimeout(), TimeUnit.SECONDS)
                .build();

        String jsonBody = String.format("""
            {
                "model": "%s",
                "messages": [
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": "%s"},
                            {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,%s"}}
                        ]
                    }
                ]
            }
            """, aiProperties.getModel(), prompt.replace("\"", "\\\""), base64Image);

        Request request = new Request.Builder()
                .url(aiProperties.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + aiProperties.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("AI API 调用失败: " + response);
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
}
