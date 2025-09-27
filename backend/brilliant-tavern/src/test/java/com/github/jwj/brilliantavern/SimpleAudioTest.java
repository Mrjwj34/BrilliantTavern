package com.github.jwj.brilliantavern;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * 简单的音频API测试，不依赖Spring上下文
 */
@Slf4j
public class SimpleAudioTest {

    @Test
    public void testPartFromBytes() {
        // 测试Part.fromBytes方法
        byte[] mockAudioData = new byte[]{0x52, 0x49, 0x46, 0x46, 0x24, 0x08, 0x00, 0x00}; // RIFF header
        String mimeType = "audio/wav";
        
        try {
            Part audioPart = Part.fromBytes(mockAudioData, mimeType);
            System.out.println("✅ 音频Part创建成功: " + audioPart);
            
            // 测试多模态Content
            Content content = Content.fromParts(
                Part.fromText("请分析这段音频"),
                audioPart
            );
            System.out.println("✅ 多模态Content创建成功: " + content);
            
        } catch (Exception e) {
            System.err.println("❌ 音频处理失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Test
    public void testMimeTypes() {
        // 测试不同MIME类型
        String[] mimeTypes = {
            "audio/wav", "audio/mpeg", "audio/webm", 
            "audio/ogg", "audio/m4a", "audio/flac"
        };
        
        byte[] testData = new byte[]{1, 2, 3, 4};
        
        for (String mimeType : mimeTypes) {
            try {
                Part audioPart = Part.fromBytes(testData, mimeType);
                System.out.println("✅ MIME类型 " + mimeType + " 支持");
            } catch (Exception e) {
                System.err.println("❌ MIME类型 " + mimeType + " 不支持: " + e.getMessage());
            }
        }
    }
}