package com.aidemo.myaitravelreimbursement.agent.tools;

import com.aidemo.myaitravelreimbursement.entity.BatchRecognizeTask;
import com.aidemo.myaitravelreimbursement.entity.Project;
import com.aidemo.myaitravelreimbursement.entity.RecognitionResult;
import com.aidemo.myaitravelreimbursement.entity.UploadFile;
import com.aidemo.myaitravelreimbursement.mapper.BatchRecognizeTaskMapper;
import com.aidemo.myaitravelreimbursement.mapper.RecognitionResultMapper;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.BatchRecognizeService;
import com.aidemo.myaitravelreimbursement.service.ProjectService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ProjectService projectService;
    private final UploadFileMapper uploadFileMapper;
    private final RecognitionResultMapper recognitionResultMapper;
    private final BatchRecognizeTaskMapper batchRecognizeTaskMapper;
    private final ObjectMapper objectMapper;

    @Tool("对指定的一个或多个文件触发 AI OCR 识别。入参：projectName - 项目名称（必填）、fileIds - 文件ID列表（必填，如 [1001, 1002]）")
    public String recognizeFiles(@P("projectName") String projectName, @P("fileIds") List<Long> fileIds) {
        try {
            if (fileIds == null || fileIds.isEmpty()) {
                return "请指定要识别的文件ID列表。";
            }
            Project project = projectService.getProjectByName(projectName);
            if (project == null) {
                return "未找到项目名称【" + projectName + "】的项目。";
            }
            var task = batchRecognizeService.submitTask(project.getId(), fileIds);
            return String.format("""
                已提交识别任务！
                - 任务ID：%s
                - 项目名称：%s
                - 待识别文件数：%d
                - 状态：%s
                """, task.getTaskId(), projectName, task.getTotal(), task.getStatus());
        } catch (Exception e) {
            log.error("识别文件失败", e);
            return "识别文件失败: " + e.getMessage();
        }
    }

    @Tool("获取项目内所有文件的 AI 识别结果。入参：projectName - 项目名称（必填）")
    public String getRecognitionResults(@P("projectName") String projectName) {
        try {
            Project project = projectService.getProjectByName(projectName);
            if (project == null) {
                return "未找到项目名称【" + projectName + "】的项目。";
            }
            List<UploadFile> files = uploadFileMapper.selectList(
                    new LambdaQueryWrapper<UploadFile>()
                            .eq(UploadFile::getProjectId, project.getId())
                            .orderByDesc(UploadFile::getCreatedAt)
            );

            if (files.isEmpty()) {
                return "项目【" + projectName + "】下暂无文件。";
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
                        file.getId(), file.getName(), statusStr));

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

    @Tool("等待并查询识别任务进度，阻塞约30秒后返回。入参：projectName - 项目名称（必填）。" +
            "【重要】此方法每次调用会阻塞约30秒，最多等待5次（共约150秒）。返回任务状态、总数、已处理数、待识别文件ID列表（JSON）。" +
            "当 status 为 pending 或 processing 时，请继续重复调用本方法直到任务完成或达到5次上限。")
    public String getRecognitionTaskProgress(@P("projectName") String projectName) {
        try {
            Project project = projectService.getProjectByName(projectName);
            if (project == null) {
                return "未找到项目名称【" + projectName + "】的项目。";
            }

            BatchRecognizeTask task = batchRecognizeTaskMapper.selectOne(
                    new LambdaQueryWrapper<BatchRecognizeTask>()
                            .eq(BatchRecognizeTask::getProjectId, project.getId())
                            .orderByDesc(BatchRecognizeTask::getCreatedAt)
                            .last("LIMIT 1")
            );

            if (task == null) {
                return "项目【" + projectName + "】暂无识别任务记录。";
            }

            List<Long> fileIds = null;
            if (task.getFileIds() != null && !task.getFileIds().isEmpty()) {
                try {
                    fileIds = objectMapper.readValue(task.getFileIds(), new TypeReference<List<Long>>() {});
                } catch (JsonProcessingException e) {
                    log.warn("解析 fileIds 失败: {}", task.getFileIds(), e);
                }
            }

            String statusDesc = switch (task.getStatus()) {
                case "pending" -> "等待中";
                case "processing" -> "处理中";
                case "completed" -> "已完成";
                case "failed" -> "失败";
                default -> task.getStatus();
            };

            return String.format("""
                识别任务进度：
                - 任务状态：%s（%s）
                - 总任务数：%d
                - 已处理数：%d
                - 文件ID：%s
                """,
                    task.getStatus(),
                    statusDesc,
                    task.getTotal(),
                    task.getProcessed(),
                    fileIds != null ? fileIds.toString() : "[]");
        } catch (Exception e) {
            log.error("查询识别任务进度失败", e);
            return "查询识别任务进度失败: " + e.getMessage();
        }
    }

    @Tool("轮询识别任务进度，循环等待直到任务完成或达到5次上限。入参：projectName - 项目名称（必填）。【重要】每次查询后等待约30秒再返回结果，最多执行5次轮询（共约150秒）。当 status 为 pending 或 processing 时，会自动继续轮询直到任务完成或达到上限。返回最终的任务状态、总数、已处理数、待识别文件ID列表（JSON）。")
    public String waitAndPollRecognition(@P("projectName") String projectName) {
        for (int i = 0; i < 5; i++) {
            String result = getRecognitionTaskProgress(projectName);
            if (result == null) {
                return "查询识别任务进度失败：返回结果为空";
            }
            if (result.contains("任务状态：completed") || result.contains("任务状态：failed")) {
                return result;
            }
            if (i < 4) {
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "等待被中断，当前进度：" + result;
                }
            }
        }
        return "轮询已达5次上限（约150秒），任务仍未完成。请稍后手动查询最终状态。";
    }
}
