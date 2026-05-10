package com.aidemo.myaitravelreimbursement.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 大模型配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * AI Provider: okhttp (legacy) | langchain4j
     */
    private String provider = "langchain4j";

    /**
     * API Base URL
     */
    private String baseUrl = "https://api.moonshot.cn/v1";

    /**
     * API Key
     */
    private String apiKey;

    /**
     * Model name (e.g. moonshot-v1-8k-vision-preview, qwen-vl-max)
     */
    private String model;

    /**
     * Request timeout in seconds
     */
    private int timeout = 100;

    /**
     * Temperature for generation
     */
    private double temperature = 1.0;

    /**
     * Max retries for transient errors
     */
    private int maxRetries = 3;
}
