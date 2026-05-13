package com.aidemo.myaitravelreimbursement.service.impl;

import com.aidemo.myaitravelreimbursement.dto.response.BatchRecognizeTaskVO;
import com.aidemo.myaitravelreimbursement.dto.response.RecognitionResultVO;
import com.aidemo.myaitravelreimbursement.entity.BatchRecognizeTask;
import com.aidemo.myaitravelreimbursement.entity.UploadFile;
import com.aidemo.myaitravelreimbursement.mapper.BatchRecognizeTaskMapper;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.AiRecognitionService;
import com.aidemo.myaitravelreimbursement.service.AsyncTaskExecutorService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 异步任务执行器服务实现
 * 单独拎出来避免 Spring AOP self-invocation 问题（同类内部调用 @Async 方法会失效）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskExecutorServiceImpl implements AsyncTaskExecutorService {

    private final BatchRecognizeTaskMapper taskMapper;
    private final UploadFileMapper uploadFileMapper;
    private final AiRecognitionService aiRecognitionService;
    private final ObjectMapper objectMapper;

    @Override
    @Async("batchRecognizeExecutor")
    //@Transactional
    public void executeBatchRecognizeTask(String taskId) {
        log.info("开始处理批量识别任务: taskId={}", taskId);

        BatchRecognizeTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<BatchRecognizeTask>()
                        .eq(BatchRecognizeTask::getTaskId, taskId)
        );

        if (task == null) {
            log.error("任务不存在: taskId={}", taskId);
            return;
        }

        if ("completed".equals(task.getStatus()) || "failed".equals(task.getStatus())) {
            log.warn("任务已处于终态，跳过执行: taskId={}, status={}", taskId, task.getStatus());
            return;
        }

        task.setStatus("processing");
        taskMapper.updateById(task);

        List<Long> fileIds;
        try {
            fileIds = objectMapper.readValue(task.getFileIds(), new TypeReference<List<Long>>() {});
        } catch (JsonProcessingException e) {
            log.error("解析文件ID列表失败: taskId={}", taskId, e);
            task.setStatus("failed");
            task.setErrorMessage("文件ID列表解析失败");
            taskMapper.updateById(task);
            return;
        }

        List<BatchRecognizeTaskVO.FileRecognitionResult> results = new ArrayList<>();

        for (Long fileId : fileIds) {
            BatchRecognizeTaskVO.FileRecognitionResult result;

            try {
                UploadFile file = uploadFileMapper.selectById(fileId);

                if (file == null || !file.getProjectId().equals(task.getProjectId())) {
                    result = BatchRecognizeTaskVO.FileRecognitionResult.builder()
                            .fileId(fileId)
                            .fileName(null)
                            .status("failed")
                            .error("文件不存在或不属于该项目")
                            .build();
                } else {
                    String fileType = file.getType() != null ? file.getType() : "invoice";
                    RecognitionResultVO recognitionResult = aiRecognitionService.recognize(fileId, fileType);

                    result = BatchRecognizeTaskVO.FileRecognitionResult.builder()
                            .fileId(fileId)
                            .fileName(file.getOriginalName())
                            .status("success")
                            .data(recognitionResult)
                            .build();
                }
            } catch (Exception e) {
                log.error("识别文件失败: fileId={}", fileId, e);
                result = BatchRecognizeTaskVO.FileRecognitionResult.builder()
                        .fileId(fileId)
                        .fileName(null)
                        .status("failed")
                        .error(e.getMessage() != null ? e.getMessage() : "识别失败")
                        .build();
            }

            results.add(result);

            task.setProcessed(results.size());
            try {
                task.setResults(objectMapper.writeValueAsString(results));
            } catch (JsonProcessingException e) {
                log.error("序列化结果失败: taskId={}", taskId, e);
            }
            taskMapper.updateById(task);
        }

        task.setStatus("completed");
        taskMapper.updateById(task);
        log.info("批量识别任务完成: taskId={}, processed={}/{}", taskId, task.getProcessed(), task.getTotal());
    }
}
