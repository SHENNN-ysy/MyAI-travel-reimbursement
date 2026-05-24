package com.aidemo.myaitravelreimbursement.rag.types;

import dev.langchain4j.data.embedding.Embedding;

/**
 * 含向量表示的文本分块，摄入管线末端产物。
 *
 * @param chunk        原始 Chunk
 * @param embedding    对应的向量表示
 * @param chromaId     ChromaDB 返回的文档 ID
 */
public record ChunkRecord(
        Chunk chunk,
        Embedding embedding,
        String chromaId
) {
}
