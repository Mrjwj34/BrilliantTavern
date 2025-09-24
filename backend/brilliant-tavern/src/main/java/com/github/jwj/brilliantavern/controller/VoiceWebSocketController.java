package com.github.jwj.brilliantavern.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * 语音WebSocket控制器（简化版本）
 * 处理语音聊天的基础通信
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class VoiceWebSocketController {
    
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 处理语音消息
     */
    @MessageMapping("/voice/{sessionId}")
    public void handleVoiceMessage(@DestinationVariable String sessionId, @Payload Map<String, Object> message) {
        log.debug("收到语音消息，会话ID: {}, 消息: {}", sessionId, message);

        try {
            // 简单响应确认收到消息
            Map<String, Object> response = Map.of(
                "type", "ACKNOWLEDGMENT",
                "messageId", java.util.UUID.randomUUID().toString(),
                "timestamp", System.currentTimeMillis(),
                "sessionId", sessionId
            );
            
            messagingTemplate.convertAndSend("/topic/voice/" + sessionId, response);
            
        } catch (Exception e) {
            log.error("处理语音消息失败，会话ID: {}", sessionId, e);
        }
    }
}
