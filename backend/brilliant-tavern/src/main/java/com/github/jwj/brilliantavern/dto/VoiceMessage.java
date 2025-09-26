package com.github.jwj.brilliantavern.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语音消息DTO
 * 支持原始二进制音频数据传输，提高传输效率
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceMessage {
    
    /**
     * 原始音频二进制数据
     * 直接存储音频字节流，避免Base64编码开销
     */
    private byte[] audioData;
    
    /**
     * 音频格式（如：wav, mp3, webm等）
     */
    private String audioFormat;
    
    /**
     * 消息ID
     */
    private String messageId;
    
    /**
     * 发送时间戳
     */
    private Long timestamp;

    /**
     * 音频采样率
     */
    private Integer sampleRate;

    /**
     * 音频持续时长（毫秒）
     */
    private Long durationMillis;
}
