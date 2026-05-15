package com.aidemo.myaitravelreimbursement.service.impl;

import com.aidemo.myaitravelreimbursement.common.BusinessException;
import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.common.UserContext;
import com.aidemo.myaitravelreimbursement.dto.response.BatchRecognizeTaskVO;
import com.aidemo.myaitravelreimbursement.entity.BatchRecognizeTask;
import com.aidemo.myaitravelreimbursement.entity.Project;
import com.aidemo.myaitravelreimbursement.mapper.BatchRecognizeTaskMapper;
import com.aidemo.myaitravelreimbursement.mapper.ProjectMapper;
import com.aidemo.myaitravelreimbursement.mapper.UploadFileMapper;
import com.aidemo.myaitravelreimbursement.service.AiRecognitionService;
import com.aidemo.myaitravelreimbursement.service.AsyncTaskExecutorService;
import com.aidemo.myaitravelreimbursement.service.BatchRecognizeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 批量识别任务服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchRecognizeServiceImpl implements BatchRecognizeService {

    private final BatchRecognizeTaskMapper taskMapper;
    private final ProjectMapper projectMapper;
    private final UploadFileMapper uploadFileMapper;
    private final AiRecognitionService aiRecognitionService;
    private final AsyncTaskExecutorService asyncTaskExecutorService;
    private final ObjectMapper objectMapper;

    @Override
    //@Transactional
    public BatchRecognizeTaskVO submitTask(Long projectId, List<Long> fileIds) {
        Long userId = verifyProjectOwnership(projectId);
        if (fileIds == null || fileIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "待识别文件列表不能为空");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        String fileIdsJson;
        try {
            fileIdsJson = objectMapper.writeValueAsString(fileIds);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件ID列表序列化失败");
        }

        BatchRecognizeTask task = new BatchRecognizeTask();
        task.setUserId(userId);
        task.setTaskId(taskId);
        task.setProjectId(projectId);
        task.setFileIds(fileIdsJson);
        task.setTotal(fileIds.size());
        task.setProcessed(0);
        task.setStatus("pending");
        task.setResults("[]");
        taskMapper.insert(task);

        // 异步处理（通过独立服务类调用，避免 self-invocation 问题）
        asyncTaskExecutorService.executeBatchRecognizeTask(taskId);

        return buildTaskVO(task, new ArrayList<>());
    }

    @Override
    public BatchRecognizeTaskVO getTaskProgress(String taskId) {
        BatchRecognizeTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<BatchRecognizeTask>()
                        .eq(BatchRecognizeTask::getTaskId, taskId)
        );

        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在或已过期");
        }
        Long userId = UserContext.getUserId();
        if (!Objects.equals(userId, task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该任务");
        }

        List<BatchRecognizeTaskVO.FileRecognitionResult> results = parseResults(task.getResults());

        return buildTaskVO(task, results);
    }

    /**
     * 构建任务VO
     */
    private BatchRecognizeTaskVO buildTaskVO(BatchRecognizeTask task,
            List<BatchRecognizeTaskVO.FileRecognitionResult> results) {
        int progress = 0;
        if (task.getTotal() != null && task.getTotal() > 0) {
            progress = (int) ((task.getProcessed() * 100.0) / task.getTotal());
        }

        return BatchRecognizeTaskVO.builder()
                .taskId(task.getTaskId())
                .projectId(task.getProjectId())
                .status(task.getStatus())
                .total(task.getTotal())
                .processed(task.getProcessed())
                .progress(progress)
                .errorMessage(task.getErrorMessage())
                .results(results)
                .build();
    }

    /**
     * 解析识别结果JSON
     */
    private List<BatchRecognizeTaskVO.FileRecognitionResult> parseResults(String resultsJson) {
        if (resultsJson == null || resultsJson.isEmpty() || "[]".equals(resultsJson)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(resultsJson,
                    new TypeReference<List<BatchRecognizeTaskVO.FileRecognitionResult>>() {});
        } catch (JsonProcessingException e) {
            log.error("解析识别结果失败: {}", resultsJson, e);
            return new ArrayList<>();
        }
    }

    private Long verifyProjectOwnership(Long projectId) {
        Long userId = UserContext.getUserId();
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "项目不存在");
        }
        if (!Objects.equals(userId, project.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该项目");
        }
        return userId;
    }
}
