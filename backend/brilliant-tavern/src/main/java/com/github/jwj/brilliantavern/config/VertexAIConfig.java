package com.github.jwj.brilliantavern.config;

import com.google.cloud.vertexai.VertexAI;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Google Vertex AI配置类
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "google.cloud")
public class VertexAIConfig {
    
    private String projectId;
    private VertexAIProperties vertexAi = new VertexAIProperties();
    
    @Data
    public static class VertexAIProperties {
        private String location = "us-central1";
        private String model = "gemini-2.5-flash";
        private Double temperature = 0.7;
        private Integer maxOutputTokens = 2048;
    }
    
    @Bean
    public VertexAI vertexAI() {
        log.info("初始化 Vertex AI 客户端: projectId={}, location={}", projectId, vertexAi.getLocation());
        return new VertexAI(projectId, vertexAi.getLocation());
    }
}