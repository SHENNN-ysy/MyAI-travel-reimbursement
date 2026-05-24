package com.aidemo.myaitravelreimbursement.rag.ingestion;

import com.aidemo.myaitravelreimbursement.rag.types.Document;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * SHA256 去重检查器。
 * <p>
 * 基于文档内容的 SHA256 哈希值进行去重，
 * 已在 ChromaDB 中的文档会记录其哈希到 metadata。
 */
public class IntegrityChecker {

    private final Set<String> knownHashes;

    public IntegrityChecker(Set<String> knownHashes) {
        this.knownHashes = knownHashes;
    }

    /**
     * 判断文档是否需要摄入（不在已知集合中）。
     */
    public boolean needsIngest(Document doc) {
        return !knownHashes.contains(doc.sha256());
    }

    /**
     * 提取文档的元数据 map（追加 sha256）。
     */
    public Map<String, String> enrichedMetadata(Document doc) {
        Map<String, String> meta = new HashMap<>(doc.metadata());
        meta.put("sha256", doc.sha256());
        return meta;
    }
}
