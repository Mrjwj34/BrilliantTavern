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
import java.io.ByteArrayOutputStream;
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
        if (log.isDebugEnabled()) {
            log.debug("收到语音消息，会话ID: {}，payload键: {}", sessionId, voiceData != null ? voiceData.keySet() : null);
        }

        try {
            UUID sessionUuid;
            try {
                sessionUuid = UUID.fromString(sessionId);
            } catch (IllegalArgumentException ex) {
                sendErrorMessage(sessionId, null, "非法的sessionId: " + sessionId);
                return;
            }

            // 1. 解析语音消息数据 (直接处理二进制数据)
            VoiceMessage voiceMessage = parseVoiceMessage(voiceData);
            if (voiceMessage.getAudioData() == null) {
                sendErrorMessage(sessionId, voiceMessage.getMessageId(), "缺少必要参数：audioData");
                return;
            }

            if (log.isInfoEnabled()) {
                log.info("语音消息准备处理，会话: {}，消息: {}，音频字节数: {}，格式: {}", sessionId,
                        voiceMessage.getMessageId(), voiceMessage.getAudioData().length, voiceMessage.getAudioFormat());
            }

            if (voiceMessage.getMessageId() == null) {
                voiceMessage.setMessageId(UUID.randomUUID().toString());
            }

            VoiceConversationOrchestrator.VoiceMessageWithMetadata payload =
                    new VoiceConversationOrchestrator.VoiceMessageWithMetadata(voiceMessage, voiceMessage.getMessageId());

        voiceConversationOrchestrator.processVoiceInput(sessionUuid, payload)
            .subscribe(
                event -> sendEvent(sessionId, event),
                error -> {
                log.error("语音对话流程异常，会话ID: {}", sessionId, error);
                sendErrorMessage(sessionId, voiceMessage.getMessageId(), "语音处理失败: " + (error.getMessage() != null ? error.getMessage() : "未知错误"));
                }
            );

        } catch (Exception e) {
            log.error("处理语音消息异常，会话ID: {}", sessionId, e);
            sendErrorMessage(sessionId, null, "系统异常: " + e.getMessage());
        }
    }

    /**
     * 解析语音消息数据 - 直接处理原始音频字节流
     */
    private VoiceMessage parseVoiceMessage(Map<String, Object> voiceData) {
        // 直接获取二进制音频数据 (前端需要发送byte[]格式)
        byte[] audioData = extractAudioBytes(voiceData.get("audioData"));
        String audioFormat = (String) voiceData.getOrDefault("audioFormat", "wav");
        long timestamp = resolveTimestamp(voiceData.get("timestamp"));
        
        return VoiceMessage.builder()
            .audioData(audioData)
            .audioFormat(audioFormat)
            .messageId((String) voiceData.get("messageId"))
            .timestamp(timestamp)
            .build();
    }

    private long resolveTimestamp(Object timestampObj) {
        if (timestampObj instanceof Number number) {
            return number.longValue();
        }
        if (timestampObj instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                // fall back below
            }
        }
        return System.currentTimeMillis();
    }

    private byte[] extractAudioBytes(Object audioSource) {
        if (audioSource == null) {
            return null;
        }

        if (audioSource instanceof byte[] bytes) {
            return bytes;
        }

        if (audioSource instanceof String base64) {
            String normalized = base64;
            int commaIndex = base64.indexOf(",");
            if (base64.startsWith("data:") && commaIndex > 0) {
                normalized = base64.substring(commaIndex + 1);
            }
            normalized = normalized.trim();
            if (normalized.isEmpty()) {
                return null;
            }
            return Base64.getDecoder().decode(normalized);
        }

        if (audioSource instanceof Iterable<?> iterable) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (Object o : iterable) {
                if (o instanceof Number number) {
                    outputStream.write(number.intValue());
                }
            }
            return outputStream.toByteArray();
        }

        return null;
    }

    /**
     * 发送错误消息
     */
    private void sendErrorMessage(String sessionId, String messageId, String errorMessage) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("error", errorMessage);
        if (messageId != null) {
            payload.put("messageId", messageId);
        }

        java.util.Map<String, Object> message = new java.util.HashMap<>();
        message.put("type", "ERROR");
        message.put("payload", payload);
        message.put("timestamp", System.currentTimeMillis());
        message.put("sessionId", sessionId);
        if (messageId != null) {
            message.put("messageId", messageId);
        }
        messagingTemplate.convertAndSend("/topic/voice/" + sessionId, message);
    }

    private void sendEvent(String sessionId, VoiceStreamEvent event) {
        messagingTemplate.convertAndSend("/topic/voice/" + sessionId, event.toMessagePayload());
    }
}
