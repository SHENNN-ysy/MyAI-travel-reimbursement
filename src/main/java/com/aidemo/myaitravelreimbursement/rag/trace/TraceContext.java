package com.aidemo.myaitravelreimbursement.rag.trace;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 全链路追踪上下文。
 * <p>
 * 基于 ThreadLocal 维护当前请求的 traceId，
 * 贯穿 RAG 检索链路（Query → Retrieval → Response），
 * 供 {@link TraceCollector} 写入 traces.jsonl。
 */
@Component
public class TraceContext {

    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();

    /** 生成并设置当前线程的 traceId */
    public String newTraceId() {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        TRACE_ID_HOLDER.set(traceId);
        return traceId;
    }

    /** 获取当前线程的 traceId，若无则创建 */
    public String currentTraceId() {
        String existing = TRACE_ID_HOLDER.get();
        if (existing != null) return existing;
        return newTraceId();
    }

    /** 清除当前线程的 traceId（请求结束后调用） */
    public void clear() {
        TRACE_ID_HOLDER.remove();
    }

    /** 绑定外部 traceId（用于跨线程传递，如 @Async 场景） */
    public void bind(String traceId) {
        TRACE_ID_HOLDER.set(traceId);
    }
}
