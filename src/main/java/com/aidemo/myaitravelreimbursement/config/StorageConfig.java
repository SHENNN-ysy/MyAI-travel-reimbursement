package com.aidemo.myaitravelreimbursement.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 存储配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageConfig {

    private String basePath = "D:/myAI-tool/travel-files";
    private List<String> allowedExtensions = List.of("pdf", "jpg", "jpeg", "png", "heic", "gif", "webp");
}
