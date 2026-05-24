package com.aidemo.myaitravelreimbursement.rag.retrieval.aggregator;

import com.aidemo.myaitravelreimbursement.rag.trace.TraceContext;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合内容聚合器，实现 LangChain4j {@link ContentAggregator} 接口。
 * <p>
 * LangChain4j 检索链路：
 * Query → QueryRouter → [retriever1, retriever2, ...]
 * → 各 ContentRetriever 独立检索
 * → ContentAggregator 合并多 Query 结果
 * <p>
 * 输入签名：{@code Map<Query, Collection<List<Content>>>}
 * - Key: 每个查询（可能是原始查询 + 扩展查询）
 * - Value: 每个 retriever 返回的 Content 列表
 * <p>
 * 本实现执行：去重 + 合并 + relevance-score 排序。
 */
@Slf4j
public class HybridContentAggregator implements ContentAggregator {

    private final double minScore;
    private final TraceContext traceContext;

    public HybridContentAggregator(double minScore, TraceContext traceContext) {
        this.minScore = minScore;
        this.traceContext = traceContext;
    }

    public HybridContentAggregator() {
        this(0.0, null);
    }

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {

        if (queryToContents == null || queryToContents.isEmpty()) {
            return List.of();
        }

        // 收集所有 Content，统一去重排序
        Map<String, ContentAndScore> dedupMap = new LinkedHashMap<>();

        for (Map.Entry<Query, Collection<List<Content>>> entry : queryToContents.entrySet()) {
            Collection<List<Content>> allLists = entry.getValue();
            if (allLists == null) continue;

            for (List<Content> contents : allLists) {
                if (contents == null) continue;
                for (Content content : contents) {
                    String key = makeKey(content);
                    if (!dedupMap.containsKey(key)) {
                        dedupMap.put(key, new ContentAndScore(content, extractScore(content)));
                    } else {
                        // 取得分更高的那一个
                        ContentAndScore existing = dedupMap.get(key);
                        double score = extractScore(content);
                        if (score > existing.score) {
                            dedupMap.put(key, new ContentAndScore(content, score));
                        }
                    }
                }
            }
        }

        List<Content> result = dedupMap.values().stream()
                .map(cs -> (ContentAndScore) cs)
                .filter(cs -> cs.score >= minScore)
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .map(cs -> cs.content)
                .collect(Collectors.toList());

        // ---------- 追踪记录 ----------
        List<String> sources = result.stream()
                .map(c -> {
                    try {
                        String v = c.textSegment().metadata().getString("source_file");
                        return (v != null && !v.isEmpty()) ? v : "未知来源";
                    } catch (Exception e) {
                        return "未知来源";
                    }
                })
                .distinct()
                .toList();

        String traceId = traceContext != null ? traceContext.currentTraceId() : null;
        if (traceId != null) {
            com.aidemo.myaitravelreimbursement.rag.trace.TraceDataHolder.set(
                    queryToContents.keySet().iterator().next().text(),
                    result.size(),
                    sources
            );
            log.info("[{}] RAG 检索完成: 查询={}, 命中={}, 来源={}",
                    traceId,
                    queryToContents.keySet().iterator().next().text(),
                    result.size(),
                    sources);
        }

        return result;
    }

    private static String makeKey(Content content) {
        String text = content.textSegment().text();
        String source = "";
        try {
            source = content.textSegment().metadata().getString("source_file");
        } catch (Exception e) {
            source = "";
        }
        return text.hashCode() + "@" + source;
    }

    /** 从 Content.metadata() 中提取 score */
    private static double extractScore(Content content) {
        try {
            Object scoreObj = content.metadata().get(ContentMetadata.SCORE);
            if (scoreObj instanceof Number n) return n.doubleValue();
        } catch (Exception ignored) {}
        return 0.0;
    }

    private record ContentAndScore(Content content, double score) {}
}
