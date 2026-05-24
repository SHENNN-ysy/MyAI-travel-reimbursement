package com.aidemo.myaitravelreimbursement.rag.types;

import java.util.List;

/**
 * 经预处理后的查询，供混合检索使用。
 *
 * @param originalText  原始用户查询
 * @param keywords      Lucene 分词提取的关键词列表
 * @param expandedQueries  LLM 扩展后的多版本查询（可选）
 */
public record ProcessedQuery(
        String originalText,
        List<String> keywords,
        List<String> expandedQueries
) {
    public static ProcessedQuery of(String text, List<String> keywords) {
        return new ProcessedQuery(text, keywords, List.of());
    }

    public static ProcessedQuery of(String text, List<String> keywords, List<String> expanded) {
        return new ProcessedQuery(text, keywords, expanded);
    }
}
