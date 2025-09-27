package com.github.jwj.brilliantavern.dto.voice;

import lombok.Builder;
import lombok.Value;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket语音流消息，统一封装事件类型与载荷。
 */
@Value
@Builder
public class VoiceStreamEvent {

    public enum Type {
        PROCESSING_STARTED,
        ASR_RESULT,
        AI_TEXT_SEGMENT,
        AUDIO_CHUNK,
        ROUND_COMPLETED,
        PROCESSING_COMPLETED,
        ERROR
    }

    Type type;
    String sessionId;
    String messageId;
    long timestamp;
    Map<String, Object> payload;

    public Map<String, Object> toMessagePayload() {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type.name());
        message.put("sessionId", sessionId);
        message.put("messageId", messageId);
        message.put("timestamp", timestamp);
        message.put("payload", payload != null ? payload : Map.of());
        return message;
    }
}
