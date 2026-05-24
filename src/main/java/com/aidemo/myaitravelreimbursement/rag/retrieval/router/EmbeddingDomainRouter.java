package com.aidemo.myaitravelreimbursement.rag.retrieval.router;

import com.aidemo.myaitravelreimbursement.rag.trace.TraceContext;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 Embedding 相似度的知识域路由器。
 * <p>
 * 预计算每个知识域的领域描述向量（应用启动时一次性完成），
 * 查询时将用户问题编码为向量，通过余弦相似度选择最匹配的知识域，
 * 避免每次查询都调用 LLM 做路由判断。
 * <p>
 * 工作流程：
 * <ol>
 *   <li>启动时：用同一个 EmbeddingModel 为每个知识域的描述文本生成向量（一次性）</li>
 *   <li>查询时：用户问题 → 向量 → 与各域向量计算余弦相似度 → 选择 top-k 知识域</li>
 * </ol>
 */
@Slf4j
public class EmbeddingDomainRouter implements QueryRouter {

    private final EmbeddingModel embeddingModel;
    private final Map<String, ContentRetriever> retrievers;
    private final Map<String, Embedding> domainEmbeddings;
    private final TraceContext traceContext;

    /** 每个知识域的自然语言描述，用于生成领域向量 */
    private static final Map<String, String> DOMAIN_DESCRIPTIONS = Map.of(
            "policy", "本政策文档详细规定了企业员工出差时的报销标准、票据要求、交通住宿补贴额度以及财务审批流程",
            "guide",  "本使用指南帮助用户了解如何操作报销助手系统，包括功能入口、操作步骤和常见问题的解决方式",
            "history", "本历史记录汇集了过往的出差报销案例、已处理的报销单据和常见问题解答"
    );

    /**
     * @param embeddingModel 向量模型
     * @param retrievers    知识域 → ContentRetriever 的映射
     */
    public EmbeddingDomainRouter(EmbeddingModel embeddingModel, Map<String, ContentRetriever> retrievers, TraceContext traceContext) {
        this.embeddingModel = embeddingModel;
        this.retrievers = retrievers;
        this.domainEmbeddings = new HashMap<>();
        this.traceContext = traceContext;
        initDomainEmbeddings();
    }

    /**
     * 启动时一次性为所有知识域生成领域描述向量。
     */
    private void initDomainEmbeddings() {
        for (Map.Entry<String, String> entry : DOMAIN_DESCRIPTIONS.entrySet()) {
            String domain = entry.getKey();
            String description = entry.getValue();
            try {
                Embedding embedding = embed(description);
                domainEmbeddings.put(domain, embedding);
                log.info("EmbeddingDomainRouter: 知识域 '{}' 向量初始化完成", domain);
            } catch (Exception e) {
                log.warn("EmbeddingDomainRouter: 知识域 '{}' 向量初始化失败: {}", domain, e.getMessage());
            }
        }
    }

    @Override
    public List<ContentRetriever> route(Query query) {
        if (domainEmbeddings.isEmpty()) {
            log.warn("EmbeddingDomainRouter: 无可用域向量，返回全部 retriever");
            return retrievers.values().stream().distinct().collect(Collectors.toList());
        }

        try {
            Embedding queryEmbedding = embed(query.text());

            // 计算与每个域的余弦相似度
            Map<String, Double> scores = new HashMap<>();
            for (Map.Entry<String, Embedding> entry : domainEmbeddings.entrySet()) {
                double sim = cosineSimilarity(queryEmbedding, entry.getValue());
                scores.put(entry.getKey(), sim);
            }

            // 取相似度最高的域（相似度 > 阈值才返回，否则返回全部）
            double threshold = 0.68;
            double topScore = scores.values().stream().max(Double::compareTo).orElse(0.0);

            List<String> matchedDomains;
            if (topScore > threshold) {
                matchedDomains = scores.entrySet().stream()
                        .filter(e -> e.getValue() > threshold)
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
            } else {
                // 相似度都低时，查全部域
                matchedDomains = new ArrayList<>(retrievers.keySet());
                log.debug("EmbeddingDomainRouter: 所有域相似度均低于阈值 {}，查全部域", threshold);
            }

            String traceId = traceContext != null ? traceContext.currentTraceId() : null;
            log.info("[{}] RAG 域路由: query='{}', 相似度={}, 匹配域={}, topScore={}",
                    traceId, query.text(), scores, matchedDomains, topScore);

            return matchedDomains.stream()
                    .filter(retrievers::containsKey)
                    .map(retrievers::get)
                    .distinct()
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("EmbeddingDomainRouter failed, returning all retrievers: {}", e.getMessage(), e);
            return retrievers.values().stream().distinct().collect(Collectors.toList());
        }
    }

    /** 兼容 BgeSmallZhV15EmbeddingModel 的 embed API */
    private Embedding embed(String text) {
        if (embeddingModel instanceof BgeSmallZhV15EmbeddingModel bge) {
            return bge.embed(text).content();
        } else {
            return embeddingModel.embed(text).content();
        }
    }

    /**
     * 计算两个向量的余弦相似度。
     * cosine(A, B) = dot(A, B) / (|A| * |B|)
     */
    private double cosineSimilarity(Embedding a, Embedding b) {
        List<Float> v1 = a.vectorAsList();
        List<Float> v2 = b.vectorAsList();
        int len = v1.size();

        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < len; i++) {
            double f1 = v1.get(i);
            double f2 = v2.get(i);
            dot += f1 * f2;
            normA += f1 * f1;
            normB += f2 * f2;
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
