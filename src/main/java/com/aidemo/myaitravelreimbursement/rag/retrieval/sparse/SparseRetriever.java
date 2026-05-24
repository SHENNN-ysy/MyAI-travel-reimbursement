package com.aidemo.myaitravelreimbursement.rag.retrieval.sparse;

import com.aidemo.myaitravelreimbursement.rag.ingestion.bm25.BM25Indexer;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 基于 Lucene BM25 的 Sparse（稀疏向量）检索器。
 * <p>
 * 查询时从 {@link BM25Indexer} 共享的索引目录读取数据，
 * 实现索引写入（SparseRetriever.addToIndex）与查询读取（BM25Indexer）的数据一致性。
 * <p>
 * 注意：索引的写入由 {@link BM25Indexer#index} 负责，
 * 本类仅负责查询，不维护索引状态。
 */
@Slf4j
public class SparseRetriever {

    private final Class<? extends Analyzer> analyzerClass;

    @Builder
    public SparseRetriever(Class<? extends Analyzer> analyzerClass) {
        this.analyzerClass = analyzerClass != null ? analyzerClass : StandardAnalyzer.class;
    }

    /**
     * 执行 BM25 检索。
     * <p>
     * 从 {@link BM25Indexer#getAllDirectories()} 读取所有已建立索引的知识域，
     * 对每个域执行 Lucene 查询并合并结果。
     *
     * @param queryText  查询文本
     * @param maxResults 最大返回数
     * @return 检索结果列表
     */
    public List<SparseResult> search(String queryText, int maxResults) {
        Map<String, Directory> allDirectories = BM25Indexer.getAllDirectories();

        if (allDirectories.isEmpty()) {
            log.debug("SparseRetriever: BM25 index is empty, skipping search");
            return List.of();
        }

        List<SparseResult> allResults = new ArrayList<>();

        for (Map.Entry<String, Directory> entry : allDirectories.entrySet()) {
            String domain = entry.getKey();
            Directory dir = entry.getValue();
            try {
                allResults.addAll(searchDomain(domain, dir, queryText, maxResults));
            } catch (Exception e) {
                log.warn("BM25 search failed for domain {}: {}", domain, e.getMessage());
            }
        }

        return allResults.stream()
                .sorted(Comparator.comparingDouble(SparseResult::score).reversed())
                .limit(maxResults)
                .toList();
    }

    private List<SparseResult> searchDomain(String domain, Directory dir, String queryText, int maxResults) {
        try {
            Analyzer analyzer = analyzerClass.getDeclaredConstructor().newInstance();
            QueryParser parser = new QueryParser("text", analyzer);
            Query query = parser.parse(queryText);

            IndexReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());

            ScoreDoc[] hits = searcher.search(query, maxResults).scoreDocs;
            List<SparseResult> results = new ArrayList<>();

            for (ScoreDoc hit : hits) {
                Document doc = searcher.doc(hit.doc);
                results.add(new SparseResult(
                        doc.get("text"),
                        doc.get("source"),
                        domain,
                        hit.score
                ));
            }

            reader.close();
            return results;
        } catch (Exception e) {
            log.warn("Domain search failed: domain={}", domain, e);
            return List.of();
        }
    }

    // ---------- Result type ----------

    public record SparseResult(
            String text,
            String source,
            String domain,
            double score
    ) {}
}
