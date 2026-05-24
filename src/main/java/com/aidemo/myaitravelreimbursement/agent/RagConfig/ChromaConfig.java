package com.aidemo.myaitravelreimbursement.agent.RagConfig;

import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 向量存储配置。
 *
 * ============ ChromaDB 模式（需独立部署，保留以便将来切换）============
 * ChromaDB 需要以 HTTP 服务方式运行：
 * - 远程模式：设置 rag.chroma.base-url
 * - 本地模式：docker run -p 8000:8000 chromadb/chroma 或直接启动
 *
 * ChromaDB 通过 LangChain4j Chroma 集成，不支持 embedded 持久化模式。
 *
 * 启用方式（需同步取消 InMemory 模式的 @Bean 注释）：
 * <pre>
 * return ChromaEmbeddingStore.builder()
 *         .baseUrl(baseUrl)
 *         .logRequests(true)
 *         .logResponses(true)
 *         .timeout(java.time.Duration.ofSeconds(30))
 *         .build();
 * </pre>
 * ============ InMemory 模式（当前使用，数据存于内存，重启丢失）===========
 * 使用 InMemoryEmbeddingStore，无需额外部署，数据存储在 JVM 堆内存中。
 * 优点：零依赖、零配置、启动即用
 * 缺点：应用重启后数据丢失（需重新摄入），数据量大时占用 JVM 堆内存
 *       查询为全量暴力搜索，无 HNSW 等索引加速
 * 适用于：开发调试、小数据量（< 1 万条 chunk）、原型验证
 *
 * 注意：InMemoryEmbeddingStore 不区分 collection，所有 domain（policy/guide/history）
 * 的向量存在同一个 store 中。通过 metadata 中的 "domain" 字段隔离查询。
 * 将来切换回 ChromaDB 时，只需将 @Bean 方法改回 ChromaEmbeddingStore 即可。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ChromaConfig {

    private final RagProperties ragProperties;

    /**
     * 创建向量存储 Bean。
     *
     * 当前使用 InMemoryEmbeddingStore，无需额外部署。
     * 如需切换到 ChromaDB，注释掉 InMemory 部分，取消 ChromaDB 部分的注释即可。
     */
    @Bean
    public EmbeddingStore<?> embeddingStore() {
        // ========== InMemory 模式（当前启用）==========
        log.info("向量存储使用 InMemoryEmbeddingStore（内存模式），应用重启后数据将丢失");
        return new InMemoryEmbeddingStore<>();

        // ========== ChromaDB 模式（保留以便切换）==========
        // var chroma = ragProperties.getChroma();
        // String baseUrl;
        // if (StringUtils.hasText(chroma.getBaseUrl())) {
        //     baseUrl = chroma.getBaseUrl();
        //     log.info("ChromaDB 使用远程模式，baseUrl={}", baseUrl);
        // } else {
        //     baseUrl = "http://" + chroma.getHost() + ":" + chroma.getPort();
        //     log.info("ChromaDB 使用默认地址，baseUrl={}", baseUrl);
        // }
        // LangChain4j ChromaDB 不支持 embedded 持久化，persistDirectory 配置忽略
        // 如需持久化，请启动 ChromaDB 时指定 --persist-directory
        // return ChromaEmbeddingStore.builder()
        //         .baseUrl(baseUrl)
        //         .logRequests(true)
        //         .logResponses(true)
        //         .timeout(java.time.Duration.ofSeconds(30))
        //         .build();
    }

    /** ChromaDB Collection 名称前缀（ChromaDB 模式使用，InMemory 模式下保留以便将来切换） */
    @Bean
    public String chromaCollectionPrefix() {
        return ragProperties.getChroma().getCollectionPrefix();
    }
}
