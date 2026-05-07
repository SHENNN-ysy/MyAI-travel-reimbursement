package com.aidemo.myaitravelreimbursement.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 全局配置
 * <p>
 * 当 ai.provider=langchain4j 时（默认值）创建 OpenAiChatModel Bean。
 * 支持 DashScope（通义千问）、Moonshot（Kimi）等所有 OpenAI-compatible 视觉大模型。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LangChain4jConfig {

    private final AiProperties aiProperties;

    /**
     * OpenAI-compatible ChatModel Bean
     * 使用 OpenAiChatModel，它兼容 DashScope / Moonshot / Kimi 等所有 OpenAI API 格式的视觉大模型
     */
    @Bean
    @ConditionalOnProperty(name = "ai.provider", havingValue = "langchain4j", matchIfMissing = true)
    public ChatModel chatModel() {
        log.info("初始化 LangChain4j OpenAiChatModel, model={}, baseUrl={}",
                aiProperties.getModel(), aiProperties.getBaseUrl());
        return OpenAiChatModel.builder()
                .apiKey(aiProperties.getApiKey())
                .modelName(aiProperties.getModel())
                .baseUrl(aiProperties.getBaseUrl())
                .timeout(Duration.ofSeconds(aiProperties.getTimeout()))
                .temperature(aiProperties.getTemperature())
                .maxRetries(aiProperties.getMaxRetries())
                .build();
    }
}
