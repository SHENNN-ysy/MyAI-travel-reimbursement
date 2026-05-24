package com.aidemo.myaitravelreimbursement.rag.trace;

/**
 * 追踪数据持有者（ThreadLocal），用于跨线程传递检索结果。
 * <p>
 * LangChain4j 的 RAG 检索链路在内部执行，结果不暴露给外部调用方。
 * 此 holder 作为桥梁：将 Aggregator 中的检索结果存入 ThreadLocal，
 * 由 AgentExecutorRunner 在 completion 回调中读取并写入 TraceCollector。
 * <p>
 * 请求结束后自动清空。
 */
public class TraceDataHolder {

    private static final ThreadLocal<TraceData> HOLDER = new ThreadLocal<>();

    private TraceDataHolder() {}

    /**
     * 存储检索结果
     */
    public static void set(String query, int retrievedCount, java.util.List<String> sources) {
        HOLDER.set(new TraceData(query, retrievedCount, sources));
    }

    /**
     * 读取检索结果（请求结束时由 AgentExecutorRunner 调用）
     */
    public static TraceData get() {
        return HOLDER.get();
    }

    /**
     * 清空（请求结束时必须调用）
     */
    public static void clear() {
        HOLDER.remove();
    }

    public record TraceData(String query, int retrievedCount, java.util.List<String> sources) {}
}
