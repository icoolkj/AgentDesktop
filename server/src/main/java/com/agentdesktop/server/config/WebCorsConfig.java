package com.agentdesktop.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "http://127.0.0.1:*",
                        "http://localhost:*",
                        "file://*"
                )
                .allowedMethods("GET", "PUT", "POST", "OPTIONS")
                .allowedHeaders("*");
    }
}
