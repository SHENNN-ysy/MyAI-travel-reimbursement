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
public class AiConfig {

    private String baseUrl = "https://api.moonshot.cn/v1";
    private String apiKey;
    private String model = "kimi-k2.5";
    private int timeout = 60;
}
