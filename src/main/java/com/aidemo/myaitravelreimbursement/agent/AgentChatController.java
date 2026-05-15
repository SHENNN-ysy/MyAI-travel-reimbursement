package com.aidemo.myaitravelreimbursement.agent;

import com.aidemo.myaitravelreimbursement.agent.service.AgentService;
import com.aidemo.myaitravelreimbursement.common.BusinessException;
import com.aidemo.myaitravelreimbursement.common.ErrorCode;
import com.aidemo.myaitravelreimbursement.common.Result;
import com.aidemo.myaitravelreimbursement.common.UserContext;
import com.aidemo.myaitravelreimbursement.entity.Project;
import com.aidemo.myaitravelreimbursement.mapper.ProjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

/**
 * Agent 对话控制器
 * 提供 SSE 流式对话接口和会话管理接口
 */
@Slf4j
@RestController
@RequestMapping("/projects/{projectId}/agent")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.provider", havingValue = "langchain4j", matchIfMissing = true)
@Tag(name = "AI Agent 对话", description = "AI Agent 对话接口")
public class AgentChatController {

    private final AgentService agentService;
    private final ProjectMapper projectMapper;
    private final AgentExecutorRunner agentExecutorRunner;

    private static final long SSE_TIMEOUT = 30L * 60 * 1000; // 30分钟

    @Operation(summary = "Agent 对话（SSE 流式，打字机效果）")
    @PostMapping("/chat")
    public SseEmitter chat(@PathVariable Long projectId,
                           @RequestParam(required = false) String sessionId,
                           @RequestParam String message) {
        // 验证项目归属
        Long userId = UserContext.getUserId();
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "项目不存在");
        }
        if (!Objects.equals(userId, project.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该项目");
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        // 每条对话都插入新行（前端已生成了 sessionId）
        if (sessionId == null || sessionId.isBlank()) {
            // 前端未传 sessionId（理论上不应该），生成一个
            sessionId = java.util.UUID.randomUUID().toString().replace("-", "");
        }

        // 插入一条用户对话记录
        agentService.createSession(projectId, sessionId, message);

        // 创建 SSE 连接
        final String finalSessionId = sessionId;
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitter.onCompletion(() -> log.debug("SSE completed: projectId={}, sessionId={}", projectId, finalSessionId));
        emitter.onTimeout(() -> log.debug("SSE timeout: projectId={}, sessionId={}", projectId, finalSessionId));
        emitter.onError(e -> log.error("SSE error: projectId={}, sessionId={}", projectId, finalSessionId, e));

        // 异步流式执行 Agent（打字机效果），传递用户上下文快照以支持跨线程
        UserContext.Snapshot snapshot = UserContext.capture();
        agentExecutorRunner.executeStreamAsync(projectId, sessionId, message, emitter, snapshot);

        // 立即返回 emitter（前端开始接收 SSE 事件）
        return emitter;
    }

    @Operation(summary = "新建会话")
    @PostMapping("/sessions")
    public Result<String> createSession(@PathVariable Long projectId) {
        String sessionId = agentService.createSession(projectId);
        return Result.success(sessionId);
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/sessions")
    public Result<?> listSessions(@PathVariable Long projectId) {
        return Result.success(agentService.listSessions(projectId));
    }

    @Operation(summary = "获取会话详情（某sessionId下所有用户对话，按id升序）")
    @GetMapping("/sessions/{sessionId}")
    public Result<?> getSessionDetail(@PathVariable String sessionId) {
        return Result.success(agentService.getSessionDetail(sessionId));
    }

    @Operation(summary = "删除会话（删除该sessionId下所有记录）")
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        agentService.deleteSession(sessionId);
        return Result.success();
    }
}
