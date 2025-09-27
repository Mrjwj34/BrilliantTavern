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
        SUBTITLE_STREAM,      // 字幕流式推送
        METHOD_EXECUTION,     // 方法执行
        ERROR,
        RETRY_STARTED,        // 重试开始
        RETRY_PROGRESS,       // 重试进度
        RETRY_FAILED,         // 重试失败（单次）
        ROUND_DISCARDED       // 对话轮次被丢弃
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
