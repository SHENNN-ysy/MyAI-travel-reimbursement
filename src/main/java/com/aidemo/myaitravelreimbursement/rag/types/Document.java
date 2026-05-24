package com.aidemo.myaitravelreimbursement.rag.types;

import java.util.List;
import java.util.Map;

/**
 * 原始文档，摄入管线入口。
 *
 * @param sourceFile  原始文件路径
 * @param content     提取后的纯文本内容
 * @param metadata    元数据（文件名、来源域、文件大小、SHA256 等）
 */
public record Document(
        String sourceFile,
        String content,
        Map<String, String> metadata
) {
    public String sha256() {
        return metadata.getOrDefault("sha256", "");
    }

    public String domain() {
        return metadata.getOrDefault("domain", "policy");
    }
}
