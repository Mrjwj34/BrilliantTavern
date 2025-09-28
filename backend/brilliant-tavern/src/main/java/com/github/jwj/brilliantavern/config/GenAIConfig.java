package com.github.jwj.brilliantavern.config;

import com.google.genai.Client;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Google Gen AI配置类
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "google.cloud")
public class GenAIConfig {
    
    private String projectId;
    private String apiKey;
    private GenAIProperties vertexAi = new GenAIProperties();
    
    @Data
    public static class GenAIProperties {
        private String location = "global";
        private String model = "gemini-2.5-flash";
        private Double temperature = 0.7;
        private Integer maxOutputTokens = 2048;
        private Integer thinkBudget = 0; // 思考预算，设为0禁用思考模式
        private Boolean useVertexAi = true; // 是否使用Vertex AI后端
    }
    
    @Bean
    public Client genAIClient() {
        log.info("初始化 Google Gen AI 客户端: projectId={}, location={}, useVertexAi={}", 
                projectId, vertexAi.getLocation(), vertexAi.getUseVertexAi());
        
        Client.Builder clientBuilder = Client.builder();
        
        if (Boolean.TRUE.equals(vertexAi.getUseVertexAi())) {
            // 使用Vertex AI后端
            clientBuilder
                .project(projectId)
                .location(vertexAi.getLocation())
                .vertexAI(true);
        } else {
            // 使用Gemini Developer API
            if (apiKey != null && !apiKey.isEmpty()) {
                clientBuilder.apiKey(apiKey);
            }
        }
        
        return clientBuilder.build();
    }
}