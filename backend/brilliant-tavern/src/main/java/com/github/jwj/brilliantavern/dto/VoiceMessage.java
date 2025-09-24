package com.github.jwj.brilliantavern.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语音消息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceMessage {
    
    /**
     * 音频数据（Base64编码或字节数组）
     */
    private byte[] audioData;
    
    /**
     * 音频格式（如：wav, mp3, webm等）
     */
    private String audioFormat;
    
    /**
     * 音频时长（秒）
     */
    private Double duration;
    
    /**
     * 消息ID
     */
    private String messageId;
    
    /**
     * 发送时间戳
     */
    private Long timestamp;
}
