package com.aidemo.myaitravelreimbursement.service;

import com.aidemo.myaitravelreimbursement.dto.response.BatchRecognizeTaskVO;
import java.util.List;

/**
 * 批量识别任务服务接口
 */
public interface BatchRecognizeService {

    /**
     * 提交批量识别任务
     * @param projectId 项目ID
     * @param fileIds 待识别文件ID列表
     * @return 任务信息（含 taskId）
     */
    BatchRecognizeTaskVO submitTask(Long projectId, List<Long> fileIds);

    /**
     * 查询任务进度
     * @param taskId 任务ID
     * @return 任务进度信息
     */
    BatchRecognizeTaskVO getTaskProgress(String taskId);
}
