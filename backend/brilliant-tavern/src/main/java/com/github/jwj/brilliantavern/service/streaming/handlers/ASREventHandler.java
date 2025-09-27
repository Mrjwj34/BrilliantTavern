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

@Slf4j
@Component
public class ASREventHandler implements EventHandler {

    private final Map<String, ASRContext> asrContexts = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public boolean canHandle(TagEvent tagEvent) {
        return tagEvent.getTagType() == TagEvent.TagType.ASR;
    }

    @Override
    public Flux<VoiceStreamEvent> handleEvent(TagEvent tagEvent, StreamingVoiceOrchestrator.SessionState sessionState) {
        String contextKey = sessionState.getSessionId() + "_" + sessionState.getMessageId();
        
        switch (tagEvent.getEventType()) {
            case TAG_OPENED:
                return handleASROpened(tagEvent, contextKey);
            case CONTENT_CHUNK:
                return handleASRContent(tagEvent, contextKey);
            case TAG_CLOSED:
                return handleASRClosed(tagEvent, contextKey);
            default:
                return Flux.empty();
        }
    }

    private Flux<VoiceStreamEvent> handleASROpened(TagEvent tagEvent, String contextKey) {
        ASRContext context = new ASRContext();
        context.sessionId = tagEvent.getSessionId();
        context.messageId = tagEvent.getMessageId();
        asrContexts.put(contextKey, context);
        
        log.debug("ASR标签开始: sessionId={}, messageId={}", 
                tagEvent.getSessionId(), tagEvent.getMessageId());
        
        return Flux.empty();
    }

    private Flux<VoiceStreamEvent> handleASRContent(TagEvent tagEvent, String contextKey) {
        ASRContext context = asrContexts.get(contextKey);
        if (context != null) {
            context.contentBuffer.append(tagEvent.getContent());
            log.debug("ASR内容累积: sessionId={}, messageId={}, content={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId(), tagEvent.getContent());
        }
        
        return Flux.empty();
    }

    private Flux<VoiceStreamEvent> handleASRClosed(TagEvent tagEvent, String contextKey) {
        ASRContext context = asrContexts.remove(contextKey);
        if (context == null || context.contentBuffer.length() == 0) {
            log.warn("ASR标签结束但没有内容: sessionId={}, messageId={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId());
            return Flux.empty();
        }
        
        String transcription = context.contentBuffer.toString().trim();
        if (!StringUtils.hasText(transcription)) {
            log.warn("ASR转写内容为空: sessionId={}, messageId={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId());
            return Flux.empty();
        }
        
        log.info("ASR转写完成: sessionId={}, messageId={}, transcription={}", 
                tagEvent.getSessionId(), tagEvent.getMessageId(), transcription);
        
        return Flux.just(buildASRResultEvent(tagEvent, transcription));
    }

    private VoiceStreamEvent buildASRResultEvent(TagEvent tagEvent, String transcription) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", transcription);
        payload.put("confidence", 1.0);
        payload.put("language", "zh");
        
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.ASR_RESULT)
                .sessionId(tagEvent.getSessionId())
                .messageId(tagEvent.getMessageId())
                .timestamp(Instant.now().toEpochMilli())
                .payload(payload)
                .build();
    }

    private static class ASRContext {
        String sessionId;
        String messageId;
        StringBuilder contentBuffer = new StringBuilder();
    }
}