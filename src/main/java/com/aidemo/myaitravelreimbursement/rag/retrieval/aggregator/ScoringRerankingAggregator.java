package com.aidemo.myaitravelreimbursement.rag.retrieval.aggregator;

import com.aidemo.myaitravelreimbursement.rag.trace.TraceContext;
import com.aidemo.myaitravelreimbursement.rag.trace.TraceDataHolder;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 基于 Cross-Encoder 的重排序内容聚合器。
 * <p>
 * 工作流程：
 * <pre>
 * 1. 调用内部 ContentAggregator（HybridContentAggregator）完成去重 + 合并 + RRF 排序
 * 2. 使用 ScoringModel（OnnxScoringModel/BGE-Reranker）对每个 Content 做相关性打分
 * 3. 按 cross-encoder score 降序排列，截取 topK，过滤 minScore 以下的低分结果
 * </pre>
 * <p>
 * 当 {@code rerankEnabled=false} 时，退化为普通聚合器，直接透传 delegate 的结果。
 * 模型文件需提前从 HuggingFace 下载：
 * <a href="https://huggingface.co/BAAI/bge-reranker-base/tree/main">BAAI/bge-reranker-base ONNX</a>
 */
@Slf4j
public class ScoringRerankingAggregator implements ContentAggregator {

    private final ContentAggregator delegate;
    private final ScoringModel scoringModel;
    private final double minScore;
    private final int maxResults;
    private final TraceContext traceContext;

    public ScoringRerankingAggregator(
            ContentAggregator delegate,
            ScoringModel scoringModel,
            double minScore,
            int maxResults,
            TraceContext traceContext) {
        this.delegate = delegate;
        this.scoringModel = scoringModel;
        this.minScore = minScore;
        this.maxResults = maxResults;
        this.traceContext = traceContext;
    }

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        List<Content> aggregated = delegate.aggregate(queryToContents);
        if (aggregated.isEmpty()) {
            return List.of();
        }

        if (scoringModel == null) {
            return aggregated;
        }

        String queryText = queryToContents.keySet().stream()
                .findFirst()
                .map(Query::text)
                .orElse("");

        // 将 Content 转 TextSegment，批量送入 ScoringModel 打分
        List<TextSegment> segments = aggregated.stream()
                .map(c -> c.textSegment())
                .toList();

        Response<List<Double>> scoresResponse = scoringModel.scoreAll(segments, queryText);
        List<Double> scores = scoresResponse.content();

        // 将 Content 与 cross-encoder score 配对
        Map<TextSegment, Double> segmentToScore = new HashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            segmentToScore.put(segments.get(i), scores.get(i));
        }

        // 按 score 降序，过滤 minScore，截取 maxResults
        List<Content> reranked = aggregated.stream()
                .filter(c -> {
                    Double s = segmentToScore.get(c.textSegment());
                    return s != null && s >= minScore;
                })
                .sorted((a, b) -> {
                    double sa = segmentToScore.getOrDefault(a.textSegment(), 0.0);
                    double sb = segmentToScore.getOrDefault(b.textSegment(), 0.0);
                    return Double.compare(sb, sa);
                })
                .limit(maxResults)
                .toList();

        // ---------- 追踪记录 ----------
        logReranking(queryText, aggregated.size(), reranked.size(), segmentToScore);

        return reranked;
    }

    private void logReranking(String query, int before, int after,
                              Map<TextSegment, Double> segmentToScore) {
        String traceId = traceContext != null ? traceContext.currentTraceId() : null;
        if (traceId == null) return;

        List<String> sources = segmentToScore.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(after)
                .map(e -> {
                    try {
                        String v = e.getKey().metadata().getString("source_file");
                        return (v != null && !v.isEmpty()) ? v : "未知来源";
                    } catch (Exception ex) {
                        return "未知来源";
                    }
                })
                .distinct()
                .toList();

        TraceDataHolder.set(query, after, sources);
        log.info("[{}] Cross-Encoder Reranking: query={}, 粗排={}, 精排后={}, 来源={}",
                traceId, query, before, after, sources);
    }
}
