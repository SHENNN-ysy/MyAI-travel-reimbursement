package com.aidemo.myaitravelreimbursement.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 对话请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent对话请求")
public class AgentChatRequestDTO {

    @Schema(description = "会话ID（首次对话可为空）")
    private String sessionId;

    @Schema(description = "用户消息内容")
    private String message;
}
