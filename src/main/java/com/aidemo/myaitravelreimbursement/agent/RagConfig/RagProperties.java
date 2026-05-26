package com.aidemo.myaitravelreimbursement.agent.RagConfig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 模块配置属性，映射 application.yml 的 rag.* 节点。
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** 文档根目录路径 */
    //private String docsPath = "/opt/travel-reimbursement/docs";
    private String docsPath = "D:/myAI-tool/docs";

    private ChromaProperties chroma = new ChromaProperties();

    private ChunkingProperties chunking = new ChunkingProperties();

    private SearchProperties search = new SearchProperties();

    private RerankerProperties reranker = new RerankerProperties();

    @Data
    public static class ChromaProperties {
        /** ChromaDB 服务器地址（覆盖 host:port） */
        private String baseUrl;
        /** ChromaDB 服务器地址 */
        private String host = "localhost";
        /** ChromaDB 服务器端口 */
        private int port = 8000;
        /** Collection 名称前缀，最终 collection 名称为 prefix + domain */
        private String collectionPrefix = "travel-";
    }

    @Data
    public static class ChunkingProperties {
        /** 单个 Chunk 最大字符数 */
        private int maxChunkSize = 1500;
        /** 相邻 Chunk 重叠字符数 */
        private int overlap = 100;
    }

    @Data
    public static class SearchProperties {
        /** 每次检索返回的最大结果数 */
        private int maxResults = 5;
        /** 最低相似度得分阈值（0~1），用于原始检索结果（embedding/BM25）过滤 */
        private double minScore = 0.5;
        /** RRF 融合分数最低门槛，用于 RRF 融合后过滤 */
        private double minRrfScore = 0.07;
        /** Dense（向量）检索权重 */
        private double denseWeight = 0.6;
        /** Sparse（BM25）检索权重 */
        private double sparseWeight = 0.4;
        /** RRF 融合参数 k */
        private int rrfK = 10;
        /** 是否启用重排序（reranking） */
        private boolean rerankEnabled = true;
        /** 重排序后保留的最大结果数 */
        private int rerankTopK = 5;
    }

    @Data
    public static class RerankerProperties {
        /** BGE Reranker 模型文件路径（ONNX 格式） */
        private String modelPath;
        /** 分词器配置文件路径（tokenizer.json） */
        private String tokenizerPath;
        /** 精排阶段最低分数阈值（0~1），低于此值的 Content 被丢弃 */
        private double minScore = 0.0;
        /** 是否使用 GPU（需要 onnxruntime_gpu） */
        private boolean useGpu = false;
    }
}
