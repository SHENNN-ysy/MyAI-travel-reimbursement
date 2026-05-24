package com.aidemo.myaitravelreimbursement.rag.retrieval.sparse;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Lucene BM25 的 Sparse（稀疏向量）检索器。
 * <p>
 * 提供两个静态方法：
 * <ul>
 *   <li>{@link #addToIndex(String, String, String, String)} - 添加文档到索引</li>
 *   <li>{@link #search(String, int)} - 执行 BM25 检索</li>
 * </ul>
 * <p>
 * 索引在内存中维护（{@link ByteBuffersDirectory}），
 * 由 {@link com.aidemo.myaitravelreimbursement.rag.ingestion.bm25.BM25Indexer} 负责维护。
 */
@Slf4j
public class SparseRetriever {

    private static final Map<String, Directory> DOMAIN_DIRECTORIES = new ConcurrentHashMap<>();
    private static final Map<String, IndexWriter> DOMAIN_WRITERS = new ConcurrentHashMap<>();

    private final Class<? extends Analyzer> analyzerClass;

    @Builder
    public SparseRetriever(Class<? extends Analyzer> analyzerClass) {
        this.analyzerClass = analyzerClass != null ? analyzerClass : StandardAnalyzer.class;
    }

    /**
     * 执行 BM25 检索。
     *
     * @param queryText  查询文本
     * @param maxResults 最大返回数
     * @return 检索结果列表
     */
    public List<SparseResult> search(String queryText, int maxResults) {
        List<SparseResult> allResults = new ArrayList<>();

        for (Map.Entry<String, Directory> entry : DOMAIN_DIRECTORIES.entrySet()) {
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

    /**
     * 添加文档到指定域的 Lucene 索引。
     */
    public static void addToIndex(String domain, String docId, String text, String sourceFile) {
        try {
            Directory dir = DOMAIN_DIRECTORIES.computeIfAbsent(domain, d -> new ByteBuffersDirectory());
            IndexWriter writer = DOMAIN_WRITERS.computeIfAbsent(domain, d -> {
                try {
                    return createWriter(dir);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Document doc = new Document();
            doc.add(new org.apache.lucene.document.StringField("id", docId, org.apache.lucene.document.Field.Store.YES));
            doc.add(new org.apache.lucene.document.TextField("text", text, org.apache.lucene.document.Field.Store.YES));
            doc.add(new org.apache.lucene.document.TextField("source", sourceFile, org.apache.lucene.document.Field.Store.YES));
            doc.add(new org.apache.lucene.document.StringField("domain", domain, org.apache.lucene.document.Field.Store.YES));

            writer.updateDocument(new Term("id", docId), doc);
            writer.commit();
        } catch (Exception e) {
            log.error("Failed to add document to BM25 index: id={}", docId, e);
        }
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

    private static IndexWriter createWriter(Directory dir) throws Exception {
        StandardAnalyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer)
                .setSimilarity(new BM25Similarity())
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        return new IndexWriter(dir, config);
    }

    // ---------- Result type ----------

    public record SparseResult(
            String text,
            String source,
            String domain,
            double score
    ) {}
}
