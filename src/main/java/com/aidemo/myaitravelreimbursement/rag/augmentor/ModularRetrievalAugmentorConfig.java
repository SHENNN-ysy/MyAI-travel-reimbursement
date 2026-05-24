package com.aidemo.myaitravelreimbursement.rag.augmentor;

import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import lombok.extern.slf4j.Slf4j;

/**
 * RetrievalAugmentor 配置工厂。
 * <p>
 * 封装 LangChain4j {@link DefaultRetrievalAugmentor} 的构建逻辑，
 * 将 QueryTransformer、QueryRouter、ContentRetriever、ContentAggregator、ContentInjector
 * 五件套组装为完整的 RetrievalAugmentor。
 */
@Slf4j
public class ModularRetrievalAugmentorConfig {

    /**
     * 构建完整的 RetrievalAugmentor。
     *
     * @param queryTransformer   查询变换器
     * @param queryRouter       查询路由器
     * @param contentAggregator 内容聚合器
     * @param contentInjector   内容注入器
     * @return 配置好的 RetrievalAugmentor
     */
    public static RetrievalAugmentor buildRetrievalAugmentor(
            QueryTransformer queryTransformer,
            QueryRouter queryRouter,
            ContentAggregator contentAggregator,
            ContentInjector contentInjector) {

        log.info("构建 RetrievalAugmentor: transformer={}, router={}, aggregator={}, injector={}",
                queryTransformer.getClass().getSimpleName(),
                queryRouter.getClass().getSimpleName(),
                contentAggregator.getClass().getSimpleName(),
                contentInjector.getClass().getSimpleName());

        return DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .queryRouter(queryRouter)
                .contentAggregator(contentAggregator)
                .contentInjector(contentInjector)
                .build();
    }

    /**
     * 构建简单的单路 RetrievalAugmentor（无 QueryRouter，仅一个 ContentRetriever）。
     *
     * @param retriever      内容检索器
     * @param contentInjector 内容注入器
     * @return 配置好的 RetrievalAugmentor
     */
    public static RetrievalAugmentor buildSimpleRetrievalAugmentor(
            ContentRetriever retriever,
            ContentInjector contentInjector) {

        log.info("构建简单 RetrievalAugmentor: retriever={}, injector={}",
                retriever.getClass().getSimpleName(),
                contentInjector.getClass().getSimpleName());

        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(retriever)
                .contentInjector(contentInjector)
                .build();
    }
}
