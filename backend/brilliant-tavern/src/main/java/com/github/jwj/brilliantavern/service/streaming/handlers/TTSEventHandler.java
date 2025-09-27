package com.github.jwj.brilliantavern.service.streaming.handlers;

import com.github.jwj.brilliantavern.dto.voice.VoiceStreamEvent;
import com.github.jwj.brilliantavern.service.TTSManagerService;
import com.github.jwj.brilliantavern.service.streaming.StreamingVoiceOrchestrator;
import com.github.jwj.brilliantavern.service.streaming.TagEvent;
import com.github.jwj.brilliantavern.service.tts.TTSStreamChunk;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class TTSEventHandler implements EventHandler {

    private final TTSManagerService ttsManagerService;
    private final Map<String, TTSContext> ttsContexts = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public boolean canHandle(TagEvent tagEvent) {
        return tagEvent.getTagType() == TagEvent.TagType.TSS;
    }

    @Override
    public Flux<VoiceStreamEvent> handleEvent(TagEvent tagEvent, StreamingVoiceOrchestrator.SessionState sessionState) {
        String contextKey = sessionState.getSessionId() + "_" + sessionState.getMessageId();

        return switch (tagEvent.getEventType()) {
            case TAG_OPENED -> handleTTSOpened(tagEvent, contextKey);
            case CONTENT_CHUNK -> handleTTSContent(tagEvent, contextKey);
            case TAG_CLOSED -> handleTTSClosed(tagEvent, contextKey, sessionState);
        };
    }

    private Flux<VoiceStreamEvent> handleTTSOpened(TagEvent tagEvent, String contextKey) {
        TTSContext context = new TTSContext();
        context.language = tagEvent.getLanguage();
        context.sessionId = tagEvent.getSessionId();
        context.messageId = tagEvent.getMessageId();
        ttsContexts.put(contextKey, context);
        
        log.debug("TTS标签开始: sessionId={}, messageId={}, language={}", 
                tagEvent.getSessionId(), tagEvent.getMessageId(), tagEvent.getLanguage());
        
        return Flux.empty();
    }

    private Flux<VoiceStreamEvent> handleTTSContent(TagEvent tagEvent, String contextKey) {
        TTSContext context = ttsContexts.get(contextKey);
        if (context != null) {
            context.contentBuffer.append(tagEvent.getContent());
            log.debug("TTS内容累积: sessionId={}, messageId={}, content={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId(), tagEvent.getContent());
        }
        
        return Flux.empty();
    }

    private Flux<VoiceStreamEvent> handleTTSClosed(TagEvent tagEvent, String contextKey, 
                                                    StreamingVoiceOrchestrator.SessionState sessionState) {
        TTSContext context = ttsContexts.remove(contextKey);
        if (context == null || context.contentBuffer.length() == 0) {
            log.warn("TTS标签结束但没有内容: sessionId={}, messageId={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId());
            return Flux.empty();
        }
        
        String ttsText = context.contentBuffer.toString().trim();
        if (!StringUtils.hasText(ttsText)) {
            log.warn("TTS文本为空: sessionId={}, messageId={}", 
                    tagEvent.getSessionId(), tagEvent.getMessageId());
            return Flux.empty();
        }
        
        log.info("开始TTS处理: sessionId={}, messageId={}, text={}", 
                tagEvent.getSessionId(), tagEvent.getMessageId(), ttsText);
        
        String voiceId = getVoiceId(sessionState);
        
        return ttsManagerService.streamSpeechWithVoice(ttsText, voiceId, sessionState.getSessionId(), sessionState.getMessageId())
                .doOnSubscribe(sub -> sessionState.getMetrics().mark("tts_start"))
                .doOnNext(chunk -> {
                    if (chunk.getAudioData() != null && chunk.getAudioData().length > 0) {
                        sessionState.getMetrics().markIfAbsent("tts_first_chunk");
                    }
                })
                .doFinally(signal -> sessionState.getMetrics().markIfAbsent("tts_completed"))
                .map(chunk -> buildAudioChunkEvent(tagEvent, chunk, context.segmentOrder.getAndIncrement()))
                .onErrorResume(error -> {
                    sessionState.getMetrics().markIfAbsent("tts_completed");
                    log.error("TTS生成失败: sessionId={}, messageId={}", 
                            tagEvent.getSessionId(), tagEvent.getMessageId(), error);
                    return Flux.just(buildErrorEvent(tagEvent, "TTS生成失败: " + error.getMessage()));
                });
    }

    private VoiceStreamEvent buildAudioChunkEvent(TagEvent tagEvent, TTSStreamChunk chunk, int segmentOrder) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("segmentOrder", segmentOrder);
        payload.put("chunkIndex", chunk.getChunkIndex());
        payload.put("isLast", chunk.isLast());
        payload.put("audioFormat", chunk.getAudioFormat() != null 
                ? chunk.getAudioFormat().name().toLowerCase() : "wav");
        payload.put("fromCache", chunk.isFromCache());
        
        if (chunk.getSampleRate() != null) {
            payload.put("sampleRate", chunk.getSampleRate());
        }
        if (chunk.getChannels() != null) {
            payload.put("channels", chunk.getChannels());
        }
        if (chunk.getBitsPerSample() != null) {
            payload.put("bitsPerSample", chunk.getBitsPerSample());
        }
        if (chunk.getAudioData() != null && chunk.getAudioData().length > 0) {
            payload.put("audioData", chunk.getAudioData());
        }

        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.AUDIO_CHUNK)
                .sessionId(tagEvent.getSessionId())
                .messageId(tagEvent.getMessageId())
                .timestamp(Instant.now().toEpochMilli())
                .payload(payload)
                .build();
    }

    private VoiceStreamEvent buildErrorEvent(TagEvent tagEvent, String errorMessage) {
        return VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.ERROR)
                .sessionId(tagEvent.getSessionId())
                .messageId(tagEvent.getMessageId())
                .timestamp(Instant.now().toEpochMilli())
                .payload(Map.of("error", errorMessage))
                .build();
    }

    private String getVoiceId(StreamingVoiceOrchestrator.SessionState sessionState) {
        String voiceIdRaw = sessionState.getSessionInfo().getCharacterCard().getTtsVoiceId();
        return StringUtils.hasText(voiceIdRaw) ? voiceIdRaw : "default";
    }

    private static class TTSContext {
        String language;
        String sessionId;
        String messageId;
        StringBuilder contentBuffer = new StringBuilder();
        AtomicInteger segmentOrder = new AtomicInteger(0);
    }
}