package com.ron.ronaiagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 检查是否允许所有源
        boolean allowAllOrigins = allowedOrigins.contains("*") ||
                                 allowedOrigins.stream().anyMatch(origin -> "*".equals(origin.trim()));

        if (allowAllOrigins) {
            // 开发环境：允许所有源，但不允许凭证（避免CORS安全限制）
            registry.addMapping("/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
                    .allowedHeaders("*")
                    .exposedHeaders("*")
                    .maxAge(3600);
        } else {
            // 生产环境：允许特定源并支持凭证
            registry.addMapping("/**")
                    .allowedOrigins(allowedOrigins.toArray(new String[0]))
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
                    .allowCredentials(true)
                    .allowedHeaders("*")
                    .exposedHeaders("*")
                    .maxAge(3600);
        }
    }
}
