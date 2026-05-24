package com.aidemo.myaitravelreimbursement.agent.RagConfig;

/**
 * RAG 模块配置已拆分至此包：
 * <ul>
 *   <li>{@link RagProperties} - application.yml rag.* 配置绑定</li>
 *   <li>{@link ChromaConfig} - 向量存储 Bean（当前为 InMemoryEmbeddingStore，保留 ChromaDB 切换注释）</li>
 *   <li>{@link RetrievalConfig} - 各知识域 ContentRetriever + LangChain4j Advanced RAG 五件套</li>
 * </ul>
 * <p>
 * 原有的 embeddingModel()、help_embeddingStore()、help_contentRetriever() Bean
 * 已迁移至 {@link RetrievalConfig}，此处保留为空文件以避免 Spring 扫描路径变更。
 */
public class RagConfig {
    // 所有配置已迁移至 RagProperties、ChromaConfig、RetrievalConfig
}
