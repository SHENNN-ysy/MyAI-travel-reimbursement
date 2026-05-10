package com.aidemo.myaitravelreimbursement.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 会话列表项 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent会话列表项")
public class AgentSessionVO {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "状态: 0-活跃 1-已完成")
    private Integer status;

    @Schema(description = "状态名称")
    private String statusName;

    @Schema(description = "最后一条用户消息摘要")
    private String lastMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
