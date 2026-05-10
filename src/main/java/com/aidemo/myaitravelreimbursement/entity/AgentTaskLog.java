package com.aidemo.myaitravelreimbursement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Agent 任务执行日志实体
 */
@Data
@TableName("t_agent_task_log")
public class AgentTaskLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Integer stepOrder;

    private String toolName;

    private String toolInput;

    private String toolOutput;

    private Integer executionTime;

    private Integer resultStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
