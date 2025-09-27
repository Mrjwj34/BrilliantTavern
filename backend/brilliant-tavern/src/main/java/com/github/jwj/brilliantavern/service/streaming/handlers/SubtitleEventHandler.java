package com.github.jwj.brilliantavern.service.streaming.handlers;

import com.github.jwj.brilliantavern.dto.voice.VoiceStreamEvent;
import com.github.jwj.brilliantavern.service.streaming.StreamingVoiceOrchestrator;
import com.github.jwj.brilliantavern.service.streaming.TagEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class SubtitleEventHandler implements EventHandler {

    private final Map<String, SubtitleContext> subtitleContexts = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public boolean canHandle(TagEvent tagEvent) {
        return tagEvent.getTagType() == TagEvent.TagType.SUB;
    }

    @Override
    public Flux<VoiceStreamEvent> handleEvent(TagEvent tagEvent, StreamingVoiceOrchestrator.SessionState sessionState) {
        String contextKey = sessionState.getSessionId() + "_" + sessionState.getMessageId();
        
        switch (tagEvent.getEventType()) {
            case TAG_OPENED:
                return handleSubtitleOpened(tagEvent, contextKey);
            case CONTENT_CHUNK:
                return handleSubtitleContent(tagEvent, contextKey);
            case TAG_CLOSED:
                return handleSubtitleClosed(tagEvent, contextKey);
            default:
                return Flux.empty();
        }
    }

    private Flux<VoiceStreamEvent> handleSubtitleOpened(TagEvent tagEvent, String contextKey) {
        SubtitleContext context = new SubtitleContext();
        context.language = tagEvent.getLanguage();
        context.sessionId = tagEvent.getSessionId();
        context.messageId = tagEvent.getMessageId();
        subtitleContexts.put(contextKey, context);
        
        log.debug("字幕标签开始: sessionId={}, messageId={}, language={}", 
                tagEvent.getSessionId(), tagEvent.getMessageId(), tagEvent.getLanguage());
        
        return Flux.just(buildSubtitleStartEvent(tagEvent));
    }

    private Flux<VoiceStreamEvent> handleSubtitleContent(TagEvent tagEvent, String contextKey) {
        SubtitleContext context = subtitleContexts.get(contextKey);
        if (context == null) {
            log.warn("字幕上下文不存在: sessionId={}, messageId={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId());
            return Flux.empty();
        }
        
        String content = tagEvent.getContent();
        if (!StringUtils.hasText(content)) {
            return Flux.empty();
        }
        
        context.contentBuffer.append(content);
        int segmentOrder = context.segmentOrder.getAndIncrement();
        
        log.debug("字幕内容流式推送: sessionId={}, messageId={}, segmentOrder={}, content={}", 
                tagEvent.getSessionId(), tagEvent.getMessageId(), segmentOrder, content);
        
        return Flux.just(buildSubtitleSegmentEvent(tagEvent, content, segmentOrder, false));
    }

    private Flux<VoiceStreamEvent> handleSubtitleClosed(TagEvent tagEvent, String contextKey) {
        SubtitleContext context = subtitleContexts.remove(contextKey);
        if (context == null) {
            log.warn("字幕标签结束但上下文不存在: sessionId={}, messageId={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId());
            return Flux.empty();
        }
        
        log.debug("字幕标签结束: sessionId={}, messageId={}, 总内容长度={}", 
                tagEvent.getSessionId(), tagEvent.getMessageId(), context.contentBuffer.length());
        
        String fullContent = context.contentBuffer.toString();
        return Flux.just(buildSubtitleEndEvent(tagEvent, fullContent));
    }

    private VoiceStreamEvent buildSubtitleStartEvent(TagEvent tagEvent) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "start");
        payload.put("language", tagEvent.getLanguage());
        
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.SUBTITLE_STREAM)
                .sessionId(tagEvent.getSessionId())
                .messageId(tagEvent.getMessageId())
                .timestamp(Instant.now().toEpochMilli())
                .payload(payload)
                .build();
    }

    private VoiceStreamEvent buildSubtitleSegmentEvent(TagEvent tagEvent, String content, int segmentOrder, boolean isFinal) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "segment");
        payload.put("segmentOrder", segmentOrder);
        payload.put("text", content);
        payload.put("isFinal", isFinal);
        
        String processedText = processActionMarkup(content);
        payload.put("processedText", processedText);
        
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.SUBTITLE_STREAM)
                .sessionId(tagEvent.getSessionId())
                .messageId(tagEvent.getMessageId())
                .timestamp(Instant.now().toEpochMilli())
                .payload(payload)
                .build();
    }

    private VoiceStreamEvent buildSubtitleEndEvent(TagEvent tagEvent, String fullContent) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "end");
        payload.put("fullText", fullContent);
        payload.put("processedText", processActionMarkup(fullContent));
        
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.SUBTITLE_STREAM)
                .sessionId(tagEvent.getSessionId())
                .messageId(tagEvent.getMessageId())
                .timestamp(Instant.now().toEpochMilli())
                .payload(payload)
                .build();
    }

    private String processActionMarkup(String text) {
        if (text == null) {
            return "";
        }
        
        return text.replaceAll("\\*([^*]+)\\*", "<action>$1</action>");
    }

    private static class SubtitleContext {
        String language;
        String sessionId;
        String messageId;
        StringBuilder contentBuffer = new StringBuilder();
        AtomicInteger segmentOrder = new AtomicInteger(0);
    }
}