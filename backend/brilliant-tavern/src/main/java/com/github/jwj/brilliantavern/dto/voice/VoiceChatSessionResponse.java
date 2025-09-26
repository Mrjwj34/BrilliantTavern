package com.github.jwj.brilliantavern.dto.voice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 语音聊天会话响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceChatSessionResponse {
    
    /**
     * 会话ID
     */
    private UUID sessionId;
    
    /**
     * 角色卡ID
     */
    private UUID characterCardId;
    
    /**
     * 角色卡名称
     */
    private String characterName;
    
    /**
     * 问候语
     */
    private String greetingMessage;
    
    /**
     * TTS语音ID
     */
    private String ttsVoiceId;
    
    /**
     * 会话创建时间
     */
    private OffsetDateTime createdAt;
    
    /**
     * WebSocket连接端点
     */
    private String websocketEndpoint;

    /**
     * STOMP订阅目的地
     */
    private String subscriptionDestination;

    /**
     * 发送语音消息的目的地
     */
    private String publishDestination;
}
