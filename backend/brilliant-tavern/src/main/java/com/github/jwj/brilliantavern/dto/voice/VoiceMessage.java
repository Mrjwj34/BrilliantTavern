package com.github.jwj.brilliantavern.dto.voice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket语音消息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceMessage {
    
    /**
     * 消息类型
     */
    private MessageType type;
    
    /**
     * 音频数据（Base64编码）
     */
    private String audioData;
    
    /**
     * 文本内容（用于字幕显示）
     */
    private String text;
    
    /**
     * 消息ID
     */
    private String messageId;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    /**
     * 是否是流式响应的最后一块
     */
    @Builder.Default
    private Boolean isLast = false;
    
    /**
     * 消息类型枚举
     */
    public enum MessageType {
        // 用户音频输入
        USER_AUDIO,
        // AI文本响应（用于字幕）
        AI_TEXT,
        // AI音频响应
        AI_AUDIO,
        // 系统消息
        SYSTEM,
        // 错误消息
        ERROR,
        // 会话结束
        SESSION_END
    }
}
