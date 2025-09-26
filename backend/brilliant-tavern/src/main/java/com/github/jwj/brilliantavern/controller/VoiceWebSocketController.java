package com.github.jwj.brilliantavern.controller;

import com.github.jwj.brilliantavern.dto.VoiceMessage;
import com.github.jwj.brilliantavern.dto.voice.VoiceStreamEvent;
import com.github.jwj.brilliantavern.service.VoiceConversationOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * 语音WebSocket控制器
 * 处理完整的语音对话工作流程：音频接收 → AI处理 → TTS生成 → 返回音频
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class VoiceWebSocketController {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final VoiceConversationOrchestrator voiceConversationOrchestrator;

    /**
     * 处理语音消息 - 完整的语音对话流程
     */
    @MessageMapping("/voice/{sessionId}")
    public void handleVoiceMessage(@DestinationVariable String sessionId, @Payload Map<String, Object> voiceData) {
        log.debug("收到语音消息，会话ID: {}", sessionId);

        try {
            // 1. 解析语音消息数据 (直接处理二进制数据)
            VoiceMessage voiceMessage = parseVoiceMessage(voiceData);
            if (voiceMessage.getAudioData() == null) {
                sendErrorMessage(sessionId, "缺少必要参数：audioData");
                return;
            }

            if (voiceMessage.getMessageId() == null) {
                voiceMessage.setMessageId(UUID.randomUUID().toString());
            }

            UUID sessionUuid = UUID.fromString(sessionId);
            VoiceConversationOrchestrator.VoiceMessageWithMetadata payload =
                    new VoiceConversationOrchestrator.VoiceMessageWithMetadata(voiceMessage, voiceMessage.getMessageId());

            voiceConversationOrchestrator.processVoiceInput(sessionUuid, payload)
                    .subscribe(
                            event -> sendEvent(sessionId, event),
                            error -> {
                                log.error("语音对话流程异常，会话ID: {}", sessionId, error);
                                sendErrorMessage(sessionId, "语音处理失败: " + error.getMessage());
                            }
                    );

        } catch (Exception e) {
            log.error("处理语音消息异常，会话ID: {}", sessionId, e);
            sendErrorMessage(sessionId, "系统异常: " + e.getMessage());
        }
    }

    /**
     * 解析语音消息数据 - 直接处理原始音频字节流
     */
    private VoiceMessage parseVoiceMessage(Map<String, Object> voiceData) {
        // 直接获取二进制音频数据 (前端需要发送byte[]格式)
        byte[] audioData = extractAudioBytes(voiceData.get("audioData"));
        String audioFormat = (String) voiceData.getOrDefault("audioFormat", "wav");
        Number sampleRate = (Number) voiceData.get("sampleRate");
        Number duration = (Number) voiceData.get("durationMillis");

        Object timestampRaw = voiceData.get("timestamp");
        Long timestamp = timestampRaw instanceof Number ? ((Number) timestampRaw).longValue() : System.currentTimeMillis();

        return VoiceMessage.builder()
            .audioData(audioData)
            .audioFormat(audioFormat)
            .messageId((String) voiceData.get("messageId"))
            .timestamp(timestamp)
            .sampleRate(sampleRate != null ? sampleRate.intValue() : null)
            .durationMillis(duration != null ? duration.longValue() : null)
            .build();
    }

    private byte[] extractAudioBytes(Object audioData) {
        if (audioData instanceof byte[] bytes) {
            return bytes;
        }
        if (audioData instanceof String base64) {
            try {
                return Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException ex) {
                log.warn("无法解析的音频Base64数据: {}", ex.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * 发送错误消息
     */
    private void sendErrorMessage(String sessionId, String errorMessage) {
        Map<String, Object> message = Map.of(
            "type", "ERROR",
            "error", errorMessage,
            "timestamp", System.currentTimeMillis(),
            "sessionId", sessionId
        );
        messagingTemplate.convertAndSend("/topic/voice/" + sessionId, message);
    }

    private void sendEvent(String sessionId, VoiceStreamEvent event) {
        messagingTemplate.convertAndSend("/topic/voice/" + sessionId, event.toMessagePayload());
    }
}
