package com.aidemo.myaitravelreimbursement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 批量识别任务进度响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRecognizeTaskVO {

    /** 任务唯一标识（UUID） */
    private String taskId;

    /** 所属项目ID */
    private Long projectId;

    /** 任务状态：pending / processing / completed / failed */
    private String status;

    /** 总任务数 */
    private Integer total;

    /** 已完成数 */
    private Integer processed;

    /** 进度百分比（0~100） */
    private Integer progress;

    /** 错误信息 */
    private String errorMessage;

    /** 各文件识别结果 */
    private List<FileRecognitionResult> results;

    /**
     * 单个文件的识别结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileRecognitionResult {
        /** 文件ID */
        private Long fileId;
        /** 文件名 */
        private String fileName;
        /** 识别状态：success / failed */
        private String status;
        /** 识别结果（识别成功时） */
        private RecognitionResultVO data;
        /** 错误信息（识别失败时） */
        private String error;
    }
}
