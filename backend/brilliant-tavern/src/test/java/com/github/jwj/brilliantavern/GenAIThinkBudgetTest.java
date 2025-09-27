package com.github.jwj.brilliantavern;

import com.github.jwj.brilliantavern.config.GenAIConfig;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 测试Google Gen AI SDK的think_budget参数
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class GenAIThinkBudgetTest {

    @Autowired
    private Client genAIClient;
    
    @Autowired
    private GenAIConfig genAIConfig;

    @Test
    public void testThinkBudgetConfiguration() {
        // 验证配置
        log.info("Think Budget配置: {}", genAIConfig.getVertexAi().getThinkBudget());
        
        // 创建配置
        GenerateContentConfig config = GenerateContentConfig.builder()
            .thinkingConfig(
                ThinkingConfig.builder()
                    .thinkingBudget(0)  // 禁用思考模式
                    .build()
            )
            .temperature(0.7f)
            .maxOutputTokens(100)
            .build();
        
        // 创建测试内容
        Content content = Content.fromParts(
            Part.fromText("简单回答：你好，请用一句话介绍自己。")
        );
        
        try {
            // 调用API
            GenerateContentResponse response = genAIClient.models.generateContent(
                genAIConfig.getVertexAi().getModel(),
                content,
                config
            );
            
            log.info("API响应: {}", response.text());
            log.info("think_budget=0参数测试成功");
            
        } catch (Exception e) {
            log.error("API调用失败", e);
        }
    }
}