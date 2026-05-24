package com.aidemo.myaitravelreimbursement.rag.retrieval.router;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;

import java.util.List;

/**
 * 固定路由器：将所有查询路由到所有 ContentRetriever。
 * <p>
 * 作为 {@link KnowledgeDomainRouter} 的 fallback 实现。
 */
public class FixedRouter implements QueryRouter {

    private final List<ContentRetriever> retrievers;

    public FixedRouter(List<ContentRetriever> retrievers) {
        this.retrievers = retrievers;
    }

    @Override
    public List<ContentRetriever> route(Query query) {
        return retrievers;
    }
}
