package com.aidemo.myaitravelreimbursement.rag.ingestion.bm25;

import com.aidemo.myaitravelreimbursement.rag.types.ChunkRecord;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BM25 全文索引器，基于 Lucene。
 * <p>
 * 为每个知识域维护独立的 Lucene Index，
 * 使用 StandardAnalyzer 进行中英文分词。
 * <p>
 * 索引字段：
 * - id: ChromaDB 文档 ID（StringField，不分词）
 * - text: 分块文本（TextField，分词+BM25）
 * - source: 来源文件（TextField）
 * - domain: 知识域（StringField）
 */
@Slf4j
@Component
public class BM25Indexer {

    /** 静态单例持有器，供 SparseRetriever 查询时读取索引 */
    private static volatile BM25Indexer INSTANCE;

    @PostConstruct
    public void init() {
        BM25Indexer.INSTANCE = this;
    }

    /** 每个知识域的索引目录 */
    private final Map<String, Directory> domainIndexes = new ConcurrentHashMap<>();

    /** 每个知识域的 IndexWriter */
    private final Map<String, IndexWriter> domainWriters = new ConcurrentHashMap<>();

    /**
     * 返回所有已建立索引的知识域及其对应的 Lucene Directory。
     * 供 {@link com.aidemo.myaitravelreimbursement.rag.retrieval.sparse.SparseRetriever} 查询时使用。
     */
    public static Map<String, Directory> getAllDirectories() {
        BM25Indexer instance = INSTANCE;
        return instance != null ? instance.domainIndexes : Map.of();
    }

    /**
     * 为单个 ChunkRecord 建立索引。
     *
     * @param record 包含 ChromaDB ID 和分块文本的记录
     */
    public synchronized void index(ChunkRecord record) {
        String domain = record.chunk().metadata().getOrDefault("domain", "policy");
        String chromaId = record.chromaId();

        try {
            IndexWriter writer = getOrCreateWriter(domain);
            Document doc = new Document();
            doc.add(new StringField("id", chromaId, Field.Store.YES));
            doc.add(new TextField("text", record.chunk().text(), Field.Store.YES));
            doc.add(new TextField("source", record.chunk().sourceFile(), Field.Store.YES));
            doc.add(new StringField("domain", domain, Field.Store.YES));

            writer.updateDocument(new Term("id", chromaId), doc);
            writer.commit();
        } catch (IOException e) {
            log.error("BM25 index failed for id={}", chromaId, e);
        }
    }

    /**
     * 获取指定域的 Lucene Directory。
     */
    public Directory getDirectory(String domain) {
        return domainIndexes.computeIfAbsent(domain, d -> new ByteBuffersDirectory());
    }

    private IndexWriter getOrCreateWriter(String domain) throws IOException {
        return domainWriters.computeIfAbsent(domain, d -> {
            try {
                Directory dir = getDirectory(d);
                StandardAnalyzer analyzer = new StandardAnalyzer();
                IndexWriterConfig config = new IndexWriterConfig(analyzer)
                        .setSimilarity(new BM25Similarity())
                        .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                return new IndexWriter(dir, config);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create IndexWriter for domain: " + d, e);
            }
        });
    }

    /** 关闭所有 IndexWriter */
    public synchronized void close() {
        domainWriters.values().forEach(w -> {
            try {
                w.close();
            } catch (IOException e) {
                log.error("Failed to close IndexWriter", e);
            }
        });
        domainWriters.clear();
    }
}
