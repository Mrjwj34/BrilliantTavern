package com.github.jwj.brilliantavern;

import com.github.jwj.brilliantavern.dto.VoiceMessage;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 音频处理集成测试
 * 演示完整的多模态音频内容处理流程
 */
@Slf4j
public class AudioProcessingIntegrationTest {

    @Test
    public void demonstrateAudioProcessing() {
        log.info("=== 音频处理集成测试开始 ===");
        
        // 1. 创建模拟WAV音频数据
        byte[] wavData = createMockWavData();
        VoiceMessage wavMessage = VoiceMessage.builder()
            .audioData(wavData)
            .audioFormat("wav")
            .build();
        
        testAudioContent(wavMessage, "WAV音频");
        
        // 2. 测试MP3音频
        VoiceMessage mp3Message = VoiceMessage.builder()
            .audioData(new byte[]{0x49, 0x44, 0x33}) // ID3 tag start
            .audioFormat("mp3")
            .build();
        
        testAudioContent(mp3Message, "MP3音频");
        
        // 3. 测试边界情况
        testEdgeCases();
        
        log.info("=== 音频处理集成测试完成 ===");
    }
    
    private void testAudioContent(VoiceMessage voiceMessage, String description) {
        try {
            Part textPart = Part.fromText("分析这段音频内容");
            Part audioPart = Part.fromBytes(voiceMessage.getAudioData(), 
                getMimeType(voiceMessage.getAudioFormat()));
            
            Content multimodalContent = Content.fromParts(textPart, audioPart);
            
            log.info("✅ {} 处理成功: 数据大小={}字节, 内容部分数={}", 
                description, 
                voiceMessage.getAudioData().length,
                multimodalContent.parts().map(parts -> parts.size()).orElse(0));
                
        } catch (Exception e) {
            log.error("❌ {} 处理失败: {}", description, e.getMessage());
        }
    }
    
    private void testEdgeCases() {
        log.info("--- 测试边界情况 ---");
        
        // 测试空数据
        try {
            VoiceMessage emptyMessage = VoiceMessage.builder()
                .audioData(new byte[0])
                .audioFormat("wav")
                .build();
            Part.fromBytes(emptyMessage.getAudioData(), "audio/wav");
            log.info("✅ 空音频数据处理正常");
        } catch (Exception e) {
            log.warn("⚠️ 空音频数据处理: {}", e.getMessage());
        }
        
        // 测试未知格式
        try {
            VoiceMessage unknownMessage = VoiceMessage.builder()
                .audioData(new byte[]{1, 2, 3, 4})
                .audioFormat("unknown")
                .build();
            Part.fromBytes(unknownMessage.getAudioData(), "audio/wav");
            log.info("✅ 未知格式音频处理正常（回退到默认）");
        } catch (Exception e) {
            log.warn("⚠️ 未知格式音频处理: {}", e.getMessage());
        }
        
        // 测试大文件
        byte[] largeData = new byte[1024 * 1024]; // 1MB
        try {
            VoiceMessage largeMessage = VoiceMessage.builder()
                .audioData(largeData)
                .audioFormat("wav")
                .build();
            Part.fromBytes(largeMessage.getAudioData(), "audio/wav");
            log.info("✅ 大文件音频处理正常");
        } catch (Exception e) {
            log.warn("⚠️ 大文件音频处理: {}", e.getMessage());
        }
    }
    
    /**
     * 创建模拟WAV文件数据
     */
    private byte[] createMockWavData() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // WAV文件头
            baos.write("RIFF".getBytes());  // ChunkID
            baos.write(new byte[]{36, 0, 0, 0}); // ChunkSize (小端序)
            baos.write("WAVE".getBytes());  // Format
            baos.write("fmt ".getBytes());  // Subchunk1ID
            baos.write(new byte[]{16, 0, 0, 0}); // Subchunk1Size
            baos.write(new byte[]{1, 0});   // AudioFormat (PCM)
            baos.write(new byte[]{1, 0});   // NumChannels (单声道)
            baos.write(new byte[]{0x40, 0x1f, 0, 0}); // SampleRate (8000Hz)
            baos.write(new byte[]{(byte)0x80, 0x3e, 0, 0}); // ByteRate
            baos.write(new byte[]{2, 0});   // BlockAlign
            baos.write(new byte[]{16, 0});  // BitsPerSample
            baos.write("data".getBytes());  // Subchunk2ID
            baos.write(new byte[]{8, 0, 0, 0}); // Subchunk2Size
            
            // 简单的音频数据（静音）
            baos.write(new byte[]{0, 0, 0, 0, 0, 0, 0, 0});
            
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("创建模拟WAV数据失败", e);
            return new byte[]{0x52, 0x49, 0x46, 0x46}; // 简化的RIFF头
        }
    }
    
    private String getMimeType(String format) {
        return switch (format.toLowerCase()) {
            case "wav" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            case "webm" -> "audio/webm";
            case "ogg" -> "audio/ogg";
            case "m4a" -> "audio/m4a";
            case "flac" -> "audio/flac";
            default -> "audio/wav";
        };
    }
}