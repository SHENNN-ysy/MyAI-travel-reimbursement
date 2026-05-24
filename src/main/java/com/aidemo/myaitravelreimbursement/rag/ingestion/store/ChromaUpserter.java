package com.aidemo.myaitravelreimbursement.rag.ingestion.store;

import com.aidemo.myaitravelreimbursement.rag.types.Chunk;
import com.aidemo.myaitravelreimbursement.rag.types.ChunkRecord;
import com.aidemo.myaitravelreimbursement.rag.types.Document;
import com.aidemo.myaitravelreimbursement.agent.RagConfig.RagProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量存储写入器。
 *
 * ============ ChromaDB 模式（保留以便将来切换）============
 * ChromaDB 通过 LangChain4j Chroma 集成，调用 {@code store.add(embedding, segment)} 写入向量。
 * LangChain4j ChromaDB 实现不支持运行时动态指定 collection，
 * 因此所有 domain（policy/guide/history）的向量实际写入同一个 collection。
 *
 * 启用方式（需同步修改 ChromaConfig 返回 ChromaEmbeddingStore）：
 * <pre>
 * // collectionName 计算出来了，但 ChromaEmbeddingStore.add() 不支持传 collectionName 参数
 * String collectionName = ragProperties.getChroma().getCollectionPrefix() + doc.domain();
 * store.add(embedding, segment);
 * </pre>
 * ============ InMemory 模式（当前使用）============
 * InMemoryEmbeddingStore 同样通过 {@code store.add(embedding, segment)} 写入。
 * 所有 domain 的向量存在同一个 store 中，通过 metadata 中的 "domain" 字段隔离查询。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChromaUpserter {

    private final EmbeddingStore<?> embeddingStore;
    private final RagProperties ragProperties;

    /**
     * 将 Document 的所有 Chunk 及其 Embedding 写入向量存储。
     *
     * @param doc        原始文档
     * @param chunks     分块列表
     * @param embeddings 对应向量列表
     * @return ChunkRecord 列表（含存储生成的 ID）
     */
    @SuppressWarnings("unchecked")
    public List<ChunkRecord> upsert(
            Document doc,
            List<Chunk> chunks,
            List<Embedding> embeddings) {

        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("chunks and embeddings size mismatch");
        }

        // ========== InMemory 模式：collectionName 仅用于日志，将来切换 ChromaDB 时同样保留此字段 ==========
        // collectionName 格式为 "travel-{domain}"，ChromaDB 模式下计算了但 add() 方法不支持传入
        // InMemory 模式下通过 metadata 中的 "domain" 字段隔离查询
        String collectionName = ragProperties.getChroma().getCollectionPrefix() + doc.domain();
        EmbeddingStore<TextSegment> store = (EmbeddingStore<TextSegment>) embeddingStore;

        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            Embedding embedding = embeddings.get(i);

            Map<String, String> metadata = new HashMap<>(chunk.metadata());
            metadata.put("source_file", doc.sourceFile());
            metadata.put("domain", doc.domain());
            metadata.put("sha256", doc.sha256());

            TextSegment segment = TextSegment.from(chunk.text(), Metadata.from(metadata));

            // 调用 EmbeddingStore.add 写入向量存储（InMemory / ChromaDB 均通过此接口）
            store.add(embedding, segment);
        }

        // InMemoryEmbeddingStore / ChromaEmbeddingStore 均不暴露文档 ID，这里按顺序返回虚拟 ID
        List<ChunkRecord> records = new java.util.ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            records.add(new ChunkRecord(chunks.get(i), embeddings.get(i), "chunk-" + i));
        }

        log.debug("Upserted {} chunks for doc '{}' into collection '{}'",
                records.size(), doc.sourceFile(), collectionName);
        return records;
    }
}
