package com.aidemo.myaitravelreimbursement.agent.RagConfig;

import com.aidemo.myaitravelreimbursement.rag.retrieval.aggregator.HybridContentAggregator;
import com.aidemo.myaitravelreimbursement.rag.retrieval.injector.MarkdownContentInjector;
import com.aidemo.myaitravelreimbursement.rag.retrieval.retriever.HybridSearchContentRetriever;
import com.aidemo.myaitravelreimbursement.rag.retrieval.router.KnowledgeDomainRouter;
import com.aidemo.myaitravelreimbursement.rag.retrieval.router.FixedRouter;
import com.aidemo.myaitravelreimbursement.rag.retrieval.query.DefaultQueryTransformerWrapper;
import com.aidemo.myaitravelreimbursement.rag.augmentor.ModularRetrievalAugmentorConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 检索层配置。
 * <p>
 * 定义每个知识域的 ContentRetriever，以及 LangChain4j Advanced RAG 五件套：
 * QueryTransformer、QueryRouter、ContentRetriever、ContentAggregator、ContentInjector。
 * <p>
 * 知识域通过 metadata 中的 domain 字段隔离查询：
 * - travel-policy  → docs/policy/ → domain="policy"
 * - travel-guide   → docs/guide/  → domain="guide"
 * - travel-history → docs/history/ → domain="history"
 * <p>
 * 当前使用 InMemoryEmbeddingStore，所有 domain 的向量存在同一 store 中，
 * 通过 {@code MetadataFilterBuilder.metadataKey("domain").isEqualTo(domain)} 隔离查询。
 * 将来切换到 ChromaDB 时，检索逻辑无需改动（filter 同样有效）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RetrievalConfig {

    /** 已知知识域列表，按 ChromaDB collection 名称前缀组织 */
    public static final List<String> KNOWN_DOMAINS = List.of("policy", "guide", "history");

    private final RagProperties ragProperties;
    private final EmbeddingModel embeddingModel;
    private final dev.langchain4j.store.embedding.EmbeddingStore<?> embeddingStore;
    private final String chromaCollectionPrefix;

    // ------------ ContentRetriever per domain ------------

    @Bean
    public ContentRetriever policyContentRetriever() {
        return hybridRetrieverFor("policy");
    }

    @Bean
    public ContentRetriever guideContentRetriever() {
        return hybridRetrieverFor("guide");
    }

    @Bean
    public ContentRetriever historyContentRetriever() {
        return hybridRetrieverFor("history");
    }

    private HybridSearchContentRetriever hybridRetrieverFor(String domain) {
        String collectionName = chromaCollectionPrefix + domain;
        var search = ragProperties.getSearch();
        return new HybridSearchContentRetriever(
                embeddingModel,
                embeddingStore,
                collectionName,
                search.getMaxResults(),
                search.getMinScore(),
                search.getDenseWeight(),
                search.getSparseWeight(),
                search.getRrfK()
        );
    }

    /** 所有知识域 ContentRetriever 的 Map，供 Router 使用 */
    @Bean
    public Map<String, ContentRetriever> domainRetrievers(
            ContentRetriever policyContentRetriever,
            ContentRetriever guideContentRetriever,
            ContentRetriever historyContentRetriever) {
        return Map.of(
                "policy", policyContentRetriever,
                "guide", guideContentRetriever,
                "history", historyContentRetriever
        );
    }

    // ------------ LangChain4j Advanced RAG components ------------

    @Bean
    public QueryTransformer queryTransformer() {
        // 使用默认的 QueryTransformer，对查询不做额外变换
        // 如需中文关键词扩展，可换为 ExpandingQueryTransformer
        return new DefaultQueryTransformerWrapper();
    }

    @Bean
    public QueryRouter queryRouter(ChatModel chatModel, Map<String, ContentRetriever> domainRetrievers) {
        // LLM 驱动的知识域路由
        try {
            return new KnowledgeDomainRouter(chatModel, domainRetrievers, KNOWN_DOMAINS);
        } catch (Exception e) {
            log.warn("KnowledgeDomainRouter 初始化失败，使用 FixedRouter fallback: {}", e.getMessage());
            return new FixedRouter(domainRetrievers.values().stream().collect(Collectors.toList()));
        }
    }

    @Bean
    public ContentAggregator contentAggregator() {
        var search = ragProperties.getSearch();
        return new HybridContentAggregator(search.getMinScore());
    }

    @Bean
    public ContentInjector contentInjector() {
        return new MarkdownContentInjector();
    }

    // ------------ RetrievalAugmentor ------------

    /**
     * 构建最终的 RetrievalAugmentor。
     *
     * 注意：ContentRetriever 通过 QueryRouter 动态路由，
     * DefaultRetrievalAugmentor 在执行时会调用 QueryRouter.route() 获取对应的 retriever。
     */
    @Bean
    public RetrievalAugmentor retrievalAugmentor(
            QueryTransformer queryTransformer,
            QueryRouter queryRouter,
            ContentAggregator contentAggregator,
            ContentInjector contentInjector) {

        RetrievalAugmentor augmentor = ModularRetrievalAugmentorConfig.buildRetrievalAugmentor(
                queryTransformer,
                queryRouter,
                contentAggregator,
                contentInjector);

        log.info("RetrievalAugmentor 构建完成：{}", augmentor.getClass().getSimpleName());
        return augmentor;
    }
}
