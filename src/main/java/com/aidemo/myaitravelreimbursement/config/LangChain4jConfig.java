package com.aidemo.myaitravelreimbursement.config;

import com.aidemo.myaitravelreimbursement.agent.RagConfig.RagProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.time.Duration;

import ai.onnxruntime.OrtSession;

/**
 * LangChain4j 全局配置
 * <p>
 * 当 ai.provider=langchain4j 时（默认值）创建两个 Bean：
 * - {@link ChatModel}：非流式，用于 AI 识别等需要完整响应的场景
 * - {@link StreamingChatModel}：流式，用于 Agent 对话等需要实时推送的场景
 * 两者均兼容 DashScope / Moonshot / Kimi 等所有 OpenAI-compatible API
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LangChain4jConfig {

    private final AiProperties aiProperties;
    private final RagProperties ragProperties;

    /**
     * 非流式 ChatModel Bean（用于 AI 识别等场景）
     */
    @Bean
    @ConditionalOnProperty(name = "ai.provider", havingValue = "langchain4j", matchIfMissing = true)
    public ChatModel chatModel() {
        log.info("初始化 LangChain4j ChatModel, model={}, baseUrl={}",
                aiProperties.getModel(), aiProperties.getBaseUrl());
        return OpenAiChatModel.builder()
                .apiKey(aiProperties.getApiKey())
                .modelName(aiProperties.getModel())
                .baseUrl(aiProperties.getBaseUrl())
                .timeout(Duration.ofSeconds(aiProperties.getTimeout()))
                .temperature(aiProperties.getTemperature())
                .maxRetries(aiProperties.getMaxRetries())
                .logRequests( true)
                .logResponses(true)
                .build();
    }

    /**
     * 流式 StreamingChatModel Bean（用于 Agent 对话等场景）
     */
    @Bean
    @ConditionalOnProperty(name = "ai.provider", havingValue = "langchain4j", matchIfMissing = true)
    public StreamingChatModel streamingChatModel() {

        log.info("初始化 LangChain4j StreamingChatModel, model={}, baseUrl={}",
                aiProperties.getModel(), aiProperties.getBaseUrl());
        return OpenAiStreamingChatModel.builder()
                .apiKey(aiProperties.getApiKey())
                .modelName(aiProperties.getModel())
                .baseUrl(aiProperties.getBaseUrl())
                .timeout(Duration.ofSeconds(aiProperties.getTimeout()))
                .temperature(aiProperties.getTemperature())
                .returnThinking(true)      // parse reasoning_content from response into aiMessage.thinking()
                .sendThinking(true)        // send aiMessage.thinking() as reasoning_content in requests
                .maxTokens(16000)
                .logRequests( true)
                .logResponses(true)
                .build();
    }

    /**
     * 向量模型 Bean（用于 EmbeddingDomainRouter 和 HybridSearchContentRetriever）
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化 BgeSmallZhV15EmbeddingModel 向量模型");
        return new BgeSmallZhV15EmbeddingModel();
    }

    /**
     * Cross-Encoder 重排序模型 Bean（用于 ReRankingContentAggregator）
     * <p>
     * 基于 BAAI/bge-reranker-base ONNX 版本，对 query-chunk 对做相关性打分。
     * 模型文件需提前下载至本地目录：
     * https://huggingface.co/BAAI/bge-reranker-base/tree/main
     */
    @Bean
    public ScoringModel scoringModel() {
        var reranker = ragProperties.getReranker();
        String modelPath = reranker.getModelPath();
        String tokenizerPath = reranker.getTokenizerPath();

        if (modelPath == null || modelPath.isBlank() || tokenizerPath == null || tokenizerPath.isBlank()) {
            log.warn("Reranker 模型路径未配置，跳过 ScoringModel 初始化");
            return null;
        }

        File modelFile = new File(modelPath);
        File tokenizerFile = new File(tokenizerPath);

        if (!modelFile.exists()) {
            log.warn("Reranker 模型文件不存在: {}，跳过 ScoringModel 初始化", modelPath);
            return null;
        }
        if (!tokenizerFile.exists()) {
            log.warn("Reranker 分词器文件不存在: {}，跳过 ScoringModel 初始化", tokenizerPath);
            return null;
        }

        log.info("初始化 BGE-Reranker ONNX 模型: modelPath={}, tokenizerPath={}", modelPath, tokenizerPath);

        if (reranker.isUseGpu()) {
            log.info("使用 GPU 加速（CUDA）");
            try {
                OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
                sessionOptions.addCUDA(0);
                return new OnnxScoringModel(modelPath, sessionOptions, tokenizerPath);
            } catch (Exception e) {
                log.warn("GPU 模式初始化失败（CUDA 不可用），回退到 CPU: {}", e.getMessage());
                return new OnnxScoringModel(modelPath, tokenizerPath);
            }
        }
        return new OnnxScoringModel(modelPath, tokenizerPath);
    }
}
