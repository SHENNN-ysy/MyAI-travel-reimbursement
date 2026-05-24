package com.aidemo.myaitravelreimbursement.rag.retrieval.retriever;

import com.aidemo.myaitravelreimbursement.rag.retrieval.sparse.SparseRetriever;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 混合检索 ContentRetriever：并发执行 Dense（向量）+ Sparse（BM25）检索，
 * 使用 RRF（Reciprocal Rank Fusion）融合两个排序列表。
 * <p>
 * 实现 LangChain4j {@link ContentRetriever} 接口。
 */
@Slf4j
@RequiredArgsConstructor
public class HybridSearchContentRetriever implements ContentRetriever {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<?> embeddingStore;
    private final String collectionName;
    private final int maxResults;
    private final double minScore;
    private final double denseWeight;
    private final double sparseWeight;
    private final int rrfK;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @Override
    @SuppressWarnings("unchecked")
    public List<Content> retrieve(Query query) {
        String queryText = query.text();
        log.debug("HybridSearchContentRetriever retrieve: collection={}, query={}", collectionName, queryText);

        // collectionName 格式为 "travel-{domain}"，取出 domain 部分用于过滤
        String domain = collectionName.replace("travel-", "");

        try {
            CompletableFuture<List<Content>> denseFuture =
                    CompletableFuture.supplyAsync(() -> denseSearch(queryText, domain), executor);
            CompletableFuture<List<Content>> sparseFuture =
                    CompletableFuture.supplyAsync(() -> sparseSearch(queryText), executor);

            CompletableFuture.allOf(denseFuture, sparseFuture).join();

            List<Content> denseResults = denseFuture.get();
            List<Content> sparseResults = sparseFuture.get();

            List<Content> fused = rrfFusion(denseResults, sparseResults);

            log.debug("HybridSearch: dense={}, sparse={}, fused={}",
                    denseResults.size(), sparseResults.size(), fused.size());
            return fused;

        } catch (Exception e) {
            log.error("HybridSearch failed for query: {}", queryText, e);
            return List.of();
        }
    }

    // ---------- Dense（向量）检索 ----------

    @SuppressWarnings("unchecked")
    private List<Content> denseSearch(String queryText, String domain) {
        try {
            Embedding queryEmbedding = embed(embeddingModel, queryText);

            // ========== InMemory 模式：按 metadata domain 字段过滤 ==========
            // collectionName 格式为 "travel-{domain}"，取出 domain 部分用于过滤
            // 将来切换到 ChromaDB 时，保留此 filter 逻辑即可实现知识域隔离查询
            Filter domainFilter = MetadataFilterBuilder.metadataKey("domain").isEqualTo(domain);

            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults * 2)
                    .filter(domainFilter)
                    .build();

            EmbeddingStore<TextSegment> store = (EmbeddingStore<TextSegment>) embeddingStore;
            EmbeddingSearchResult<TextSegment> result = store.search(searchRequest);

            List<Content> contents = new ArrayList<>();
            for (var match : result.matches()) {
                if (match.score() >= minScore) {
                    Map<ContentMetadata, Object> meta = new EnumMap<>(ContentMetadata.class);
                    meta.put(ContentMetadata.SCORE, match.score());
                    contents.add(new ContentBuilder(match.embedded(), meta));
                }
            }
            return contents;
        } catch (Exception e) {
            log.warn("Dense search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 兼容 BgeSmallZhV15EmbeddingModel 的 embed API */
    private static Embedding embed(EmbeddingModel model, String text) {
        if (model instanceof BgeSmallZhV15EmbeddingModel bge) {
            return bge.embed(text).content();
        } else {
            return model.embed(text).content();
        }
    }

    // ---------- Sparse（BM25）检索 ----------

    private List<Content> sparseSearch(String queryText) {
        try {
            SparseRetriever retriever = SparseRetriever.builder()
                    .analyzerClass(org.apache.lucene.analysis.standard.StandardAnalyzer.class)
                    .build();

            List<Content> contents = new ArrayList<>();
            for (SparseRetriever.SparseResult r : retriever.search(queryText, maxResults * 2)) {
                double normalizedScore = normalizeScore(r.score(), false);
                Map<ContentMetadata, Object> meta = new EnumMap<>(ContentMetadata.class);
                meta.put(ContentMetadata.SCORE, normalizedScore);
                Metadata docMeta = Metadata.from(Map.of("source_file", r.source(), "domain", r.domain()));
                TextSegment segment = TextSegment.from(r.text(), docMeta);
                contents.add(new ContentBuilder(segment, meta));
            }
            return contents;
        } catch (Exception e) {
            log.warn("Sparse search failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ---------- RRF 融合 ----------

    private List<Content> rrfFusion(List<Content> dense, List<Content> sparse) {
        Map<String, ScoredContent> scoreMap = new LinkedHashMap<>();

        for (int i = 0; i < dense.size(); i++) {
            Content item = dense.get(i);
            String key = makeKey(item);
            double rrfScore = denseWeight / (rrfK + i + 1);
            scoreMap.merge(key, new ScoredContent(item, rrfScore),
                    (a, b) -> new ScoredContent(a.content, a.score + b.score));
        }

        for (int i = 0; i < sparse.size(); i++) {
            Content item = sparse.get(i);
            String key = makeKey(item);
            double rrfScore = sparseWeight / (rrfK + i + 1);
            scoreMap.merge(key, new ScoredContent(item, rrfScore),
                    (a, b) -> new ScoredContent(a.content, a.score + b.score));
        }

        return scoreMap.values().stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(maxResults)
                .map(ScoredContent::content)
                .toList();
    }

    private static String makeKey(Content content) {
        String text = content.textSegment().text();
        String source = "";
        try {
            Object v = content.metadata().get(ContentMetadata.SCORE);
            if (v != null) source = v.toString();
        } catch (Exception ignored) {}
        return text.hashCode() + "@" + source;
    }

    /** 归一化得分到 0~1 */
    private static double normalizeScore(double rawScore, boolean isVector) {
        if (isVector) {
            return Math.max(0, (rawScore - 0.5) * 2);
        } else {
            return Math.min(1.0, Math.max(0, rawScore / 10.0));
        }
    }

    // ---------- 内部类 ----------

    private record ScoredContent(Content content, double score) {}

    /**
     * 简化版 Content 实现，绕过静态工厂方法直接构造。
     * Content 接口的 from() 静态工厂需要 Map<ContentMetadata, Object>，
     * 这里直接实例化包内可见的实现类。
     */
    private static class ContentBuilder implements Content {
        private final TextSegment segment;
        private final Map<ContentMetadata, Object> meta;

        ContentBuilder(TextSegment segment, Map<ContentMetadata, Object> meta) {
            this.segment = segment;
            this.meta = meta;
        }

        @Override
        public TextSegment textSegment() { return segment; }

        @Override
        public Map<ContentMetadata, Object> metadata() { return meta; }

        @Override
        public String toString() {
            return "ContentBuilder{segment=" + segment + ", score=" + meta.get(ContentMetadata.SCORE) + "}";
        }
    }
}
