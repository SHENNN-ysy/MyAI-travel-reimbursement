package com.aidemo.myaitravelreimbursement.config;

import com.github.xiaoymin.knife4j.spring.extension.Knife4jOpenApiCustomizer;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 自定义 Knife4jOpenApiCustomizer，解决 springdoc 2.8.x 与 Knife4j 4.5.0 的兼容性
 * <p>
 * 问题：Knife4j 4.5.0 捆绑的 springdoc 2.3.0 存在 API 兼容性问题，
 * 升级到 springdoc 2.8.x 后 Knife4j 内置的 OpenApiCustomizer 调用了已删除的 getGroupConfigs() 方法，
 * 导致 NoSuchMethodError。本类使用反射兼容新版本 API。
 * </p>
 */
@Slf4j
@Configuration
public class Knife4jConfig {

    @Bean
    public Knife4jOpenApiCustomizer knife4jOpenApiCustomizer(
            Knife4jProperties knife4jProperties,
            SpringDocConfigProperties springDocConfigProperties) {
        return new MyKnife4jOpenApiCustomizer(knife4jProperties, springDocConfigProperties);
    }

    @Slf4j
    public static class MyKnife4jOpenApiCustomizer extends Knife4jOpenApiCustomizer {
        private final Knife4jProperties knife4jProperties;

        public MyKnife4jOpenApiCustomizer(Knife4jProperties knife4jProperties,
                                           SpringDocConfigProperties springDocConfigProperties) {
            super(knife4jProperties, springDocConfigProperties);
            this.knife4jProperties = knife4jProperties;
        }

        @Override
        public void customise(OpenAPI openApi) {
            if (!knife4jProperties.isEnable()) {
                return;
            }
            // 解析 Knife4j 设置
            com.github.xiaoymin.knife4j.spring.extension.OpenApiExtensionResolver openApiExtensionResolver =
                    new com.github.xiaoymin.knife4j.spring.extension.OpenApiExtensionResolver(
                            knife4jProperties.getSetting(), knife4jProperties.getDocuments());
            openApiExtensionResolver.start();

            Map<String, Object> objectMap = new HashMap<>();
            objectMap.put(
                    com.github.xiaoymin.knife4j.core.conf.GlobalConstants.EXTENSION_OPEN_SETTING_NAME,
                    knife4jProperties.getSetting());
            objectMap.put(
                    com.github.xiaoymin.knife4j.core.conf.GlobalConstants.EXTENSION_OPEN_MARKDOWN_NAME,
                    openApiExtensionResolver.getMarkdownFiles());
            openApi.addExtension(
                    com.github.xiaoymin.knife4j.core.conf.GlobalConstants.EXTENSION_OPEN_API_NAME, objectMap);
        }
    }
}
