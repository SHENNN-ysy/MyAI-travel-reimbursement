package com.aidemo.myaitravelreimbursement.agent.tools;

import com.aidemo.myaitravelreimbursement.entity.RecognitionResult;
import com.aidemo.myaitravelreimbursement.entity.UploadFile;
import com.aidemo.myaitravelreimbursement.mapper.RecognitionResultMapper;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.BatchRecognizeService;
import com.aidemo.myaitravelreimbursement.service.FileStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * T4 & T5: 文件识别工具（识别文件、获取识别结果）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecognitionTools {

    private final BatchRecognizeService batchRecognizeService;
    private final FileStorageService fileStorageService;
    private final UploadFileMapper uploadFileMapper;
    private final RecognitionResultMapper recognitionResultMapper;

    @Tool("对指定的一个或多个文件触发 AI OCR 识别。入参：projectId - 项目ID（必填）、fileIds - 文件ID列表（必填，如 [1001, 1002]）")
    public String recognizeFiles(@P("projectId") Long projectId, @P("fileIds") List<Long> fileIds) {
        try {
            if (fileIds == null || fileIds.isEmpty()) {
                return "请指定要识别的文件ID列表。";
            }
            var task = batchRecognizeService.submitTask(projectId, fileIds);
            return String.format("""
                已提交识别任务！
                - 任务ID：%s
                - 项目ID：%d
                - 待识别文件数：%d
                - 状态：%s
                """, task.getTaskId(), task.getProjectId(), task.getTotal(), task.getStatus());
        } catch (Exception e) {
            log.error("识别文件失败", e);
            return "识别文件失败: " + e.getMessage();
        }
    }

    @Tool("获取项目内所有文件的 AI 识别结果。入参：projectId - 项目ID（必填）")
    public String getRecognitionResults(@P("projectId") Long projectId) {
        try {
            List<UploadFile> files = uploadFileMapper.selectList(
                    new LambdaQueryWrapper<UploadFile>()
                            .eq(UploadFile::getProjectId, projectId)
                            .orderByDesc(UploadFile::getCreatedAt)
            );

            if (files.isEmpty()) {
                return "该项目下暂无文件。";
            }

            StringBuilder sb = new StringBuilder("识别结果列表：\n");
            int successCount = 0;
            int failCount = 0;
            java.math.BigDecimal totalAmount = java.math.BigDecimal.ZERO;

            for (UploadFile file : files) {
                RecognitionResult result = recognitionResultMapper.selectOne(
                        new LambdaQueryWrapper<RecognitionResult>()
                                .eq(RecognitionResult::getFileId, file.getId())
                                .orderByDesc(RecognitionResult::getCreatedAt)
                                .last("LIMIT 1")
                );

                String statusStr = switch (file.getStatus()) {
                    case 0 -> "待识别";
                    case 1 -> "识别中";
                    case 2 -> "已识别";
                    case 3 -> "识别失败";
                    default -> "未知";
                };

                sb.append(String.format("- [%d] %s | %s",
                        file.getId(), file.getOriginalName(), statusStr));

                if (result != null && file.getStatus() == 2) {
                    successCount++;
                    java.math.BigDecimal amount = result.getTotalAmount() != null
                            ? result.getTotalAmount()
                            : result.getTotalConsumption();
                    if (amount != null) {
                        totalAmount = totalAmount.add(amount);
                        sb.append(String.format(" | ¥%s | %s | %s",
                                amount.stripTrailingZeros().toPlainString(),
                                result.getExpenseType() != null ? result.getExpenseType() : "-",
                                result.getInvoiceDate() != null ? result.getInvoiceDate().toString()
                                        : (result.getConsumptionDate() != null ? result.getConsumptionDate().toString() : "-")));
                    }
                } else if (file.getStatus() == 3) {
                    failCount++;
                }
                sb.append("\n");
            }

            sb.append(String.format("\n汇总：已识别 %d 个，失败 %d 个，总金额 ¥%s",
                    successCount, failCount, totalAmount.stripTrailingZeros().toPlainString()));
            return sb.toString();
        } catch (Exception e) {
            log.error("获取识别结果失败", e);
            return "获取识别结果失败: " + e.getMessage();
        }
    }
}
