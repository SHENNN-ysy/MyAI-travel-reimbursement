package com.aidemo.myaitravelreimbursement.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Agent 会话记录实体（每行存储一条对话，sessionId分组，id升序=时间正序，role区分用户/AI）
 */
@Data
@TableName("t_agent_session")
public class AgentSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /**
     * 会话唯一ID（同一会话可有多条记录）
     */
    private String sessionId;

    private String userId;

    /**
     * 消息角色: user=用户消息, assistant=AI回复
     */
    private String role;

    /**
     * 对话内容
     */
    private String lastMessage;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
