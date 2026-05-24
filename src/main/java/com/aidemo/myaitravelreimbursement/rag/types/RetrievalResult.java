package com.aidemo.myaitravelreimbursement.rag.types;

import dev.langchain4j.data.segment.TextSegment;

/**
 * 单条检索结果。
 *
 * @param text         分块文本
 * @param source       来源文档名
 * @param domain       所属知识域
 * @param score        相似度得分（RRF 融合后归一化得分，0~1）
 * @param rank         融合后排名
 */
public record RetrievalResult(
        String text,
        String source,
        String domain,
        double score,
        int rank
) {
}
