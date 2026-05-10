package com.aidemo.myaitravelreimbursement.agent;

import com.aidemo.myaitravelreimbursement.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 执行器
 * 负责运行 LangChain4j Agent 并通过 SSE 实时推送 token 流
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.provider", havingValue = "langchain4j", matchIfMissing = true)
public class AgentExecutorRunner {

    private final ReimbursementAgent reimbursementAgent;
    private final AgentService agentService;

    /**
     * 流式 SSE 执行
     * 用户消息由 Controller 插入，AI 回复在此方法结束时插入
     */
    @Async("agentExecutor")
    public CompletableFuture<Void> executeStreamAsync(Long projectId, String sessionId,
                                                      String message, SseEmitter emitter) {
        emitter.onCompletion(() -> log.debug("SSE completed: projectId={}, sessionId={}", projectId, sessionId));
        emitter.onTimeout(() -> log.debug("SSE timeout: projectId={}, sessionId={}", projectId, sessionId));
        emitter.onError(e -> log.error("SSE error: projectId={}, sessionId={}", projectId, sessionId, e));

        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean thinkingCleared = new AtomicBoolean(false);
        long startTime = System.currentTimeMillis();

        // 累积 AI 回复内容，对话结束时保存
        StringBuilder assistantContent = new StringBuilder();

        try {
            sendEvent(emitter, "thinking", "{\"content\": \"正在思考...\"}");

            ReimbursementAgent.Assistant assistant = reimbursementAgent.createAssistant(sessionId);

            Flux<String> flux = assistant.chatStream(message);

            flux.publishOn(Schedulers.boundedElastic())
                    .subscribe(
                            token -> {
                                if (completed.get()) return;
                                if (thinkingCleared.compareAndSet(false, true)) {
                                    sendEvent(emitter, "thinking", "{\"content\": \"\"}");
                                }
                                assistantContent.append(token);
                                sendEvent(emitter, "message", "{\"content\": \"" + escapeJson(token) + "\"}");
                            },
                            error -> {
                                log.error("Agent stream error for session: {}", sessionId, error);
                                if (completed.compareAndSet(false, true)) {
                                    try {
                                        // 出错时仍保存已生成的部分回复
                                        saveAssistantMessage(sessionId, assistantContent.toString());
                                        sendEvent(emitter, "error", "{\"error\": \"" + escapeJson(error.getMessage()) + "\"}");
                                        emitter.completeWithError(error);
                                    } catch (Exception e) {
                                        log.warn("Failed to complete SSE with error", e);
                                    }
                                }
                            },
                            () -> {
                                long elapsed = System.currentTimeMillis() - startTime;
                                if (completed.compareAndSet(false, true)) {
                                    try {
                                        // 正常完成：保存 AI 回复（插入一条 role=assistant 的记录）
                                        saveAssistantMessage(sessionId, assistantContent.toString());

                                        sendEvent(emitter, "done", "{\"summary\": \"\"}");
                                        emitter.complete();
                                        log.info("Agent stream completed: sessionId={}, elapsed={}ms, response length={}",
                                                sessionId, elapsed, assistantContent.length());
                                    } catch (Exception e) {
                                        log.warn("Failed to complete SSE", e);
                                    }
                                }
                            }
                    );

        } catch (Exception e) {
            log.error("Agent stream failed: sessionId={}", sessionId, e);
            if (completed.compareAndSet(false, true)) {
                try {
                    saveAssistantMessage(sessionId, assistantContent.toString());
                    sendEvent(emitter, "error", "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    log.warn("Failed to complete SSE with error", ex);
                }
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * 保存 AI 回复到数据库（role=assistant）
     */
    private void saveAssistantMessage(String sessionId, String content) {
        if (content == null || content.isBlank()) return;

        try {
            agentService.saveAssistantMessage(sessionId, content);
        } catch (Exception e) {
            log.error("保存 AI 回复失败: sessionId={}", sessionId, e);
        }
    }

    private boolean sendEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
            return true;
        } catch (Exception e) {
            log.warn("Failed to send SSE event: {}, error: {}", eventName, e.getMessage());
            return false;
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
