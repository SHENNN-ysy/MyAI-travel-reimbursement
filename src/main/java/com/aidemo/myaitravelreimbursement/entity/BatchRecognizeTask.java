package com.aidemo.myaitravelreimbursement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 批量识别任务实体
 * 存储批量识别任务的进度和结果
 */
@Data
@TableName("t_batch_recognize_task")
public class BatchRecognizeTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务唯一标识（UUID） */
    private String taskId;

    /** 所属项目ID */
    private Long projectId;

    /** 待识别文件ID列表（JSON字符串，如 [1001,1002,1003]） */
    private String fileIds;

    /** 总任务数 */
    private Integer total;

    /** 已完成数 */
    private Integer processed;

    /** 任务状态：pending / processing / completed / failed */
    private String status;

    /** 识别结果列表（JSON字符串） */
    private String results;

    /** 错误信息 */
    private String errorMessage;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
