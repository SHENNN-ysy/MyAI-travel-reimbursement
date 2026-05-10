package com.aidemo.myaitravelreimbursement.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 消息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent对话消息")
public class AgentMessageDTO {

    @Schema(description = "角色: user/assistant")
    private String role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "消息时间戳")
    private LocalDateTime timestamp;
}
