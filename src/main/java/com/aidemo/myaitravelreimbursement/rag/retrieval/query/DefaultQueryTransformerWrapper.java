package com.aidemo.myaitravelreimbursement.rag.retrieval.query;

import dev.langchain4j.rag.query.transformer.QueryTransformer;

/**
 * LangChain4j {@link QueryTransformer} 默认实现：对查询不做额外变换，直接透传。
 * <p>
 * 如需中文关键词扩展，可替换为 {@link ExpandingQueryTransformer}。
 */
public class DefaultQueryTransformerWrapper implements QueryTransformer {

    @Override
    public java.util.List<dev.langchain4j.rag.query.Query> transform(dev.langchain4j.rag.query.Query query) {
        // 默认：不做变换，只返回原始查询
        return java.util.List.of(query);
    }
}
