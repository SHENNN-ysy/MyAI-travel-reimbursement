package com.aidemo.myaitravelreimbursement.rag.types;

import java.util.Map;

/**
 * 文本分块，分块管线输出。
 *
 * @param text        分块文本内容
 * @param metadata    元数据（所属文档 SHA256、文件名前缀、域、chunk 序号等）
 */
public record Chunk(
        String text,
        Map<String, String> metadata
) {
    public int index() {
        return Integer.parseInt(metadata.getOrDefault("chunk_index", "0"));
    }

    public String sourceFile() {
        return metadata.getOrDefault("source_file", "");
    }
}
