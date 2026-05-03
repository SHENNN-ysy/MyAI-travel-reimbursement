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

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String apiKey;
    private String model = "qwen-vl-max";
    private int timeout = 60;
}
