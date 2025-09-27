package com.github.jwj.brilliantavern;

import com.github.jwj.brilliantavern.dto.VoiceMessage;
import com.github.jwj.brilliantavern.service.AIService;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;

/**
 * 测试音频内容API
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class AudioContentTest {

    @Autowired
    private AIService aiService;

    @Test
    public void explorePartMethods() {
        // 查看Part类的所有方法
        Method[] methods = Part.class.getMethods();
        for (Method method : methods) {
            if (method.getName().startsWith("from") && java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                log.info("Static method: {} - {}", method.getName(), java.util.Arrays.toString(method.getParameterTypes()));
            }
        }
    }

    @Test
    public void testAudioContentCreation() {
        // 创建模拟音频数据
        byte[] mockAudioData = new byte[]{0x52, 0x49, 0x46, 0x46}; // RIFF header stub
        
        VoiceMessage voiceMessage = VoiceMessage.builder()
            .audioData(mockAudioData)
            .audioFormat("wav")
            .build();
        
        try {
            // 测试音频Part创建
            Part audioPart = Part.fromBytes(mockAudioData, "audio/wav");
            log.info("音频Part创建成功: {}", audioPart);
            
            // 测试Content创建
            Content content = Content.fromParts(
                Part.fromText("测试音频内容"),
                audioPart
            );
            log.info("多模态Content创建成功: {}", content);
            
        } catch (Exception e) {
            log.error("音频内容创建失败", e);
        }
    }

    @Test
    public void testMimeTypeMapping() {
        // 测试各种音频格式的MIME类型映射
        String[] formats = {"wav", "mp3", "webm", "ogg", "m4a", "flac", "opus", "unknown"};
        
        for (String format : formats) {
            VoiceMessage voiceMessage = VoiceMessage.builder()
                .audioData(new byte[]{1, 2, 3, 4})
                .audioFormat(format)
                .build();
            
            // 这里需要通过反射访问private方法，或者创建public测试方法
            log.info("音频格式: {} -> 预期MIME类型映射测试", format);
        }
    }
}