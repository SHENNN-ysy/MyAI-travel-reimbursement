package com.aidemo.myaitravelreimbursement.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

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
}
