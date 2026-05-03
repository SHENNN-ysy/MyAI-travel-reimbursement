package com.aidemo.myaitravelreimbursement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j Swagger 配置
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("差旅报销助手 API")
                        .description("Travel Reimbursement AI 后端接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("AI Demo Team")
                                .email("support@example.com")));
    }
}
