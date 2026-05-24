package com.aidemo.myaitravelreimbursement.agent;

import com.aidemo.myaitravelreimbursement.agent.service.AgentService;
import com.aidemo.myaitravelreimbursement.common.UserContext;
import com.aidemo.myaitravelreimbursement.rag.trace.TraceCollector;
import com.aidemo.myaitravelreimbursement.rag.trace.TraceContext;
import com.aidemo.myaitravelreimbursement.rag.trace.TraceDataHolder;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 执行器
 * 负责运行 LangChain4j Agent 并通过 SSE 实时推送三段式流：思考、对话、工具调用
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.provider", havingValue = "langchain4j", matchIfMissing = true)
public class AgentExecutorRunner {

    private final ReimbursementAgent reimbursementAgent;
    private final AgentService agentService;
    private final TraceContext traceContext;
    private final TraceCollector traceCollector;

    @Qualifier("sseScheduler")
    private final ScheduledExecutorService sseScheduler;

    /**
     * 三段式流式 SSE 执行
     * - partialThinking → SSE reasoning 事件（模型思考内容）
     * - partialResponse → SSE message 事件（AI 回复，打字机效果）
     * - partialToolCall  → SSE tool_call 事件（仅工具名称，不含参数）
     */
    @Async("agentExecutor")
    public CompletableFuture<Void> executeStreamAsync(Long projectId, String sessionId,
                                                     String message, SseEmitter emitter,
                                                     UserContext.Snapshot userContextSnapshot) {
        // 在异步线程中恢复用户上下文，确保 Tool 调用链能获取到 userId
        UserContext.restore(userContextSnapshot);
        UserContext.setCurrentSnapshot(userContextSnapshot);

        // 初始化追踪上下文，绑定 traceId 到 ThreadLocal
        String traceId = traceContext.newTraceId();
        traceContext.bind(traceId);
        log.debug("[{}] Agent stream started: projectId={}, sessionId={}", traceId, projectId, sessionId);

        emitter.onCompletion(() -> log.debug("SSE completed: projectId={}, sessionId={}", projectId, sessionId));
        emitter.onTimeout(() -> log.debug("SSE timeout: projectId={}, sessionId={}", projectId, sessionId));
        emitter.onError(e -> log.error("SSE error: projectId={}, sessionId={}", projectId, sessionId, e));

        AtomicBoolean completed = new AtomicBoolean(false);
        long startTime = System.currentTimeMillis();
        StringBuilder assistantContent = new StringBuilder();
        AtomicInteger toolIndexCounter = new AtomicInteger(0);
        // 批量发送 message token 的 buffer 和 flush 任务
        StringBuilder messageBuffer = new StringBuilder();
        AtomicReference<ScheduledFuture<?>> flushFutureRef = new AtomicReference<>(null);

        Runnable flushBuffer = () -> {
            if (completed.get() || messageBuffer.length() == 0) return;
            String batch = messageBuffer.toString();
            messageBuffer.setLength(0);
            assistantContent.append(batch);
            sendEvent(emitter, "message", "{\"content\": \"" + escapeJson(batch) + "\"}");
        };

        try {
            ReimbursementAgent.Assistant assistant = reimbursementAgent.createAssistant(sessionId);
            TokenStream tokenStream = assistant.chatStream(message);

            tokenStream
                    // 思考：每个 thinking chunk 都通过 SSE 推送给前端
                    .onPartialThinking((PartialThinking partialThinking) -> {
                        //System.out.println("partialThinking:"+partialThinking);
                        if (completed.get()) return;
                        if (partialThinking != null && partialThinking.text() != null) {
                            sendEvent(emitter, "reasoning", "{\"content\": \"" + escapeJson(partialThinking.text()) + "\"}");
                        }
                    })
                    // 对话：累积 token 到 buffer，每 50ms 批量发送一次
                    .onPartialResponse((String partialResponse) -> {
                        if (completed.get()) return;
                        if (partialResponse != null) {
                            messageBuffer.append(partialResponse);
                            // 首次收到 token 时启动定时 flush
                            if (flushFutureRef.get() == null) {
                                flushFutureRef.set(sseScheduler.scheduleAtFixedRate(
                                        () -> { try { flushBuffer.run(); } catch (Exception ignored) {} },
                                        50, 50, TimeUnit.MILLISECONDS));
                            }
                        }
                    })
                    // 工具调用：发送工具名称和批次内索引，前端据此区分并行调用
                    .onPartialToolCall((PartialToolCall partialToolCall) -> {
                        //System.out.println("partialThinking:"+partialToolCall.name());
                        if (completed.get()) return;
                        if (partialToolCall != null && partialToolCall.name() != null) {
                            int idx = toolIndexCounter.getAndIncrement();
                            sendEvent(emitter, "tool_call", "{\"toolIndex\": " + idx
                                    + ", \"tool\": \"" + escapeJson(partialToolCall.name()) + "\"}");
                        }
                    })
                    // 工具执行完成：发送 tool_result 事件给前端，隐藏"执行中..."
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        if (completed.get()) return;
                        if (toolExecution != null) {
                            String toolName = toolExecution.request().name();
                            boolean hidden = "getExcelFilePath".equals(toolName);
                            String resultJson = String.format(
                                    "{\"tool\": \"%s\", \"success\": true, \"output\": \"%s\", \"hidden\": %s}",
                                    escapeJson(toolName),
                                    hidden ? "" : escapeJson(String.valueOf(toolExecution.result())),
                                    hidden
                            );
                            sendEvent(emitter, "tool_result", resultJson);
                        }
                    })
                    // 完成：取消 flush，发送剩余 buffer，保存消息，发送 done 事件
                    .onCompleteResponse((ChatResponse response) -> {
                        if (completed.compareAndSet(false, true)) {
                            try {
                                // 停止定时 flush 并发送剩余内容
                                ScheduledFuture<?> f = flushFutureRef.get();
                                if (f != null) f.cancel(false);
                                flushBuffer.run();
                                saveAssistantMessage(sessionId, assistantContent.toString());

                                // 全链路追踪记录
                                TraceDataHolder.TraceData td = TraceDataHolder.get();
                                traceCollector.record(TraceCollector.builder()
                                        .traceId(traceId)
                                        .query(message)
                                        .retrievedCount(td != null ? td.retrievedCount() : 0)
                                        .sources(td != null ? td.sources() : List.of())
                                        .responseSummary(truncate(assistantContent.toString(), 200))
                                        .elapsedMs(System.currentTimeMillis() - startTime)
                                        .build());

                                sendEvent(emitter, "done", "{\"summary\": \"\"}");
                                emitter.complete();
                                UserContext.clearCurrentSnapshot();
                                traceContext.clear();
                                TraceDataHolder.clear();
                                long elapsed = System.currentTimeMillis() - startTime;
                                log.info("Agent stream completed: sessionId={}, elapsed={}ms, response length={}",
                                        sessionId, elapsed, assistantContent.length());
                            } catch (Exception e) {
                                log.warn("Failed to complete SSE", e);
                            }
                        }
                    })
                    // 异常处理
                    .onError((Throwable error) -> {
                        if (completed.compareAndSet(false, true)) {
                            try {
                                ScheduledFuture<?> f = flushFutureRef.get();
                                if (f != null) f.cancel(false);
                                flushBuffer.run();
                                saveAssistantMessage(sessionId, assistantContent.toString());
                                sendEvent(emitter, "error", "{\"error\": \"" + escapeJson(error.getMessage()) + "\"}");
                                emitter.completeWithError(error);
                                UserContext.clearCurrentSnapshot();
                                traceContext.clear();
                                TraceDataHolder.clear();
                            } catch (Exception e) {
                                log.warn("Failed to complete SSE with error", e);
                            }
                        }
                    })
                    .start();

        } catch (Exception e) {
            log.error("Agent stream failed: sessionId={}", sessionId, e);
            if (completed.compareAndSet(false, true)) {
                try {
                    ScheduledFuture<?> f = flushFutureRef.get();
                    if (f != null) f.cancel(false);
                    flushBuffer.run();
                    saveAssistantMessage(sessionId, assistantContent.toString());
                    sendEvent(emitter, "error", "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
                    emitter.completeWithError(e);
                    UserContext.clearCurrentSnapshot();
                    traceContext.clear();
                    TraceDataHolder.clear();
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

    private static String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
