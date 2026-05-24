package com.aidemo.myaitravelreimbursement.rag.ingestion;

import com.aidemo.myaitravelreimbursement.agent.RagConfig.RagProperties;
import com.aidemo.myaitravelreimbursement.agent.RagConfig.RetrievalConfig;
import com.aidemo.myaitravelreimbursement.rag.ingestion.bm25.BM25Indexer;
import com.aidemo.myaitravelreimbursement.rag.ingestion.store.ChromaUpserter;
import com.aidemo.myaitravelreimbursement.rag.providers.loader.PdfLoader;
import com.aidemo.myaitravelreimbursement.rag.providers.splitter.RecursiveTextSplitter;
import com.aidemo.myaitravelreimbursement.rag.trace.TraceContext;
import com.aidemo.myaitravelreimbursement.rag.types.Chunk;
import com.aidemo.myaitravelreimbursement.rag.types.ChunkRecord;
import com.aidemo.myaitravelreimbursement.rag.types.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;

/**
 * 文档摄入管线编排器。
 * <p>
 * 执行顺序：
 * 1. 扫描 docs 目录下的所有文档
 * 2. SHA256 去重过滤
 * 3. 分块（RecursiveTextSplitter）
 * 4. 批量编码（EmbeddingModel）
 * 5. ChromaDB 写入
 * 6. BM25 索引
 * <p>
 * 在 Spring Boot 启动时自动触发（ApplicationRunner）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionPipeline implements ApplicationRunner {

    private final PdfLoader pdfLoader;
    private final RecursiveTextSplitter splitter;
    private final EmbeddingModel embeddingModel;
    private final ChromaUpserter chromaUpserter;
    private final BM25Indexer bm25Indexer;
    private final RagProperties ragProperties;
    private final TraceContext traceContext;

    @Override
    public void run(ApplicationArguments args) {
        String traceId = traceContext.currentTraceId();
        log.info("[{}] RAG IngestionPipeline 启动，docs-path={}", traceId, ragProperties.getDocsPath());

        try {
            long start = System.currentTimeMillis();
            int count = ingestAll();
            long elapsed = System.currentTimeMillis() - start;
            log.info("[{}] RAG IngestionPipeline 完成：摄入 {} 个文档，耗时 {}ms", traceId, count, elapsed);
        } catch (Exception e) {
            log.error("[{}] RAG IngestionPipeline 执行失败", traceId, e);
        }
    }

    /**
     * 对 docs 目录下的所有子目录（policy/guide/history）执行全量摄入。
     */
    public int ingestAll() {
        Path docsRoot = Paths.get(ragProperties.getDocsPath());
        if (!docsRoot.toFile().exists()) {
            log.warn("docs-path 不存在，跳过摄入: {}", docsRoot);
            return 0;
        }

        int totalDocs = 0;
        for (String domain : RetrievalConfig.KNOWN_DOMAINS) {
            Path domainDir = docsRoot.resolve(domain);
            if (domainDir.toFile().exists()) {
                totalDocs += ingestDomain(domainDir, domain);
            } else {
                log.info("Domain 目录不存在，跳过: {}", domainDir);
            }
        }
        return totalDocs;
    }

    /**
     * 摄入单个知识域下的所有文档。
     */
    public int ingestDomain(Path domainDir, String domain) {
        String traceId = traceContext.currentTraceId();
        log.info("[{}] 开始摄入 domain={}, path={}", traceId, domain, domainDir);

        List<Path> files = pdfLoader.scan(domainDir);
        if (files.isEmpty()) {
            log.info("[{}] domain={} 无文档，跳过", traceId, domain);
            return 0;
        }

        var seenHashes = new HashSet<String>();
        int count = 0;

        for (Path file : files) {
            Document doc = pdfLoader.load(file);

            if (!seenHashes.add(doc.sha256())) {
                log.debug("SHA256 重复，跳过: {}", file.getFileName());
                continue;
            }

            try {
                ingestDocument(doc);
                count++;
                log.debug("摄入成功: {} (domain={})", file.getFileName(), domain);
            } catch (Exception e) {
                log.error("摄入文档失败: {}", file, e);
            }
        }

        log.info("[{}] domain={} 摄入完成，共 {} 个文档", traceId, domain, count);
        return count;
    }

    /**
     * 摄入单个文档。
     * <ol>
     *   <li>分块</li>
     *   <li>批量编码</li>
     *   <li>ChromaDB 写入</li>
     *   <li>BM25 索引</li>
     * </ol>
     */
    public void ingestDocument(Document doc) {
        // 1. 分块
        List<Chunk> chunks = splitter.split(doc.content(), doc.sourceFile(), doc.domain());
        if (chunks.isEmpty()) return;

        // 2. 逐条编码（BgeSmallZhV15EmbeddingModel.embed(String)）
        var embeddings = new java.util.ArrayList<Embedding>();
        for (Chunk chunk : chunks) {
            embeddings.add(embeddingModel.embed(chunk.text()).content());
        }

        // 3. ChromaDB 写入
        List<ChunkRecord> records = chromaUpserter.upsert(doc, chunks, embeddings);

        // 4. BM25 索引
        for (ChunkRecord record : records) {
            bm25Indexer.index(record);
        }
    }
}
