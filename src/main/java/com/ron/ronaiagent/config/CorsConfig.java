package com.ron.ronaiagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author admin
 * @date 2025/11/3 下午10:05
 * 描述：CORS 配置
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry  registry){
        // 允许所有请求
        registry.addMapping("/**")
                // 允许所有来源
                .allowedOriginPatterns("*")
                // 允许所有方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")
                // 允许发送 Cookie
                .allowCredentials(true)
                // 允许所有头
                .allowedHeaders("*")
                // 允许跨域
                .exposedHeaders("*")
                // 缓存 1 天
                .maxAge(360000);
    }
}
