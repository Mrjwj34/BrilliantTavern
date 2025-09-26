package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.dto.VoiceMessage;
import com.github.jwj.brilliantavern.dto.voice.SentenceSegment;
import com.github.jwj.brilliantavern.dto.voice.VoiceStreamEvent;
import com.github.jwj.brilliantavern.entity.ChatHistory;
import com.github.jwj.brilliantavern.service.AIService.AIStreamEvent;
import com.github.jwj.brilliantavern.service.AIService.ProcessedAiResponse;
import com.github.jwj.brilliantavern.service.tts.TtsChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 协调语音输入、AI回复、TTS转换与消息推送的核心服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceConversationOrchestrator {

        private final AIService aiService;
        private final SentenceSegmentationService segmentationService;
        private final TTSManagerService ttsManagerService;
        private final VoiceChatService voiceChatService;

    public Flux<VoiceStreamEvent> processVoiceInput(UUID sessionId, VoiceMessageWithMetadata voiceMessage) {
        VoiceChatService.SessionInfo sessionInfo = voiceChatService.getSession(sessionId);
                String messageId = voiceMessage.messageId();
                String voiceIdRaw = sessionInfo.getCharacterCard().getTtsVoiceId();
                final String voiceId = StringUtils.hasText(voiceIdRaw) ? voiceIdRaw : "default";

        Flux<AIStreamEvent> aiEvents = aiService.streamVoiceConversation(
                voiceMessage.voiceMessage(),
                sessionInfo.getCharacterCard(),
                sessionId.toString(),
                messageId
        ).publish().autoConnect(2);

        Flux<SentenceSegment> sentenceSegments = segmentationService
                .segment(aiEvents
                        .filter(event -> event.getType() == AIStreamEvent.Type.CHUNK)
                        .map(AIStreamEvent::getContent),
                        messageId
                );

        Mono<ProcessedAiResponse> finalResponseMono = aiEvents
                .filter(event -> event.getType() == AIStreamEvent.Type.COMPLETED)
                .next()
                .map(AIStreamEvent::getProcessedResponse)
                .switchIfEmpty(Mono.error(new IllegalStateException("AI未返回完成事件")))
                .cache();

        Flux<VoiceStreamEvent> processingStarted = Flux.just(VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.PROCESSING_STARTED)
                .sessionId(sessionId.toString())
                .messageId(messageId)
                .timestamp(Instant.now().toEpochMilli())
                .payload(Map.of(
                        "characterCardId", sessionInfo.getCharacterCardId().toString(),
                        "voiceId", voiceId
                ))
                .build());

        Flux<VoiceStreamEvent> segmentEvents = sentenceSegments.concatMap(segment ->
                Flux.concat(
                        Mono.just(VoiceStreamEvent.builder()
                                .type(VoiceStreamEvent.Type.AI_TEXT_SEGMENT)
                                .sessionId(sessionId.toString())
                                .messageId(messageId)
                                .timestamp(Instant.now().toEpochMilli())
                                .payload(Map.of(
                                        "segmentOrder", segment.getOrder(),
                                        "text", segment.getText(),
                                        "isFinal", segment.isFinal()
                                ))
                                .build()),
                        ttsManagerService.streamSpeechWithVoice(segment.getText(), voiceId)
                                .flatMap(chunk -> toAudioEvents(sessionId, messageId, segment, chunk))
                )
        );

        Flux<VoiceStreamEvent> completionEvents = finalResponseMono.flatMapMany(processed -> {
            return persistHistory(sessionInfo, sessionId, processed)
                    .thenMany(Flux.fromIterable(buildCompletionEvents(sessionId, messageId, processed)));
        });

        return processingStarted
                .concatWith(segmentEvents)
                .concatWith(completionEvents)
                .onErrorResume(error -> {
                    log.error("语音对话处理失败: sessionId={}, messageId={}", sessionId, messageId, error);
                    return Flux.just(VoiceStreamEvent.builder()
                            .type(VoiceStreamEvent.Type.ERROR)
                            .sessionId(sessionId.toString())
                            .messageId(messageId)
                            .timestamp(Instant.now().toEpochMilli())
                            .payload(Map.of("error", error.getMessage()))
                            .build());
                });
    }

    private Flux<VoiceStreamEvent> toAudioEvents(UUID sessionId,
                                                 String messageId,
                                                 SentenceSegment segment,
                                                 TtsChunk chunk) {
        if (chunk.getAudioData() == null) {
            return Flux.empty();
        }

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("segmentOrder", segment.getOrder());
        payload.put("chunkIndex", chunk.getChunkIndex());
        payload.put("audioData", chunk.getAudioData());
        payload.put("audioFormat", StringUtils.hasText(chunk.getAudioFormat()) ? chunk.getAudioFormat() : "wav");
        payload.put("isLast", chunk.isLast());

        return Flux.just(VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.AUDIO_CHUNK)
                .sessionId(sessionId.toString())
                .messageId(messageId)
                .timestamp(Instant.now().toEpochMilli())
                .payload(payload)
                .build());
    }

    private Mono<Void> persistHistory(VoiceChatService.SessionInfo sessionInfo,
                                      UUID sessionId,
                                      ProcessedAiResponse processed) {
        return Mono.fromRunnable(() -> {
            if (StringUtils.hasText(processed.userTranscription())) {
                voiceChatService.saveChatHistory(
                        sessionId,
                        sessionInfo.getUser().getId(),
                        sessionInfo.getCharacterCardId(),
                        ChatHistory.Role.USER,
                        processed.userTranscription()
                );
            }
            voiceChatService.saveChatHistory(
                    sessionId,
                    sessionInfo.getUser().getId(),
                    sessionInfo.getCharacterCardId(),
                    ChatHistory.Role.ASSISTANT,
                    processed.aiResponse()
            );
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Iterable<VoiceStreamEvent> buildCompletionEvents(UUID sessionId,
                                                             String messageId,
                                                             ProcessedAiResponse processed) {
        Instant now = Instant.now();
        var events = new java.util.ArrayList<VoiceStreamEvent>();

        if (StringUtils.hasText(processed.userTranscription())) {
            events.add(VoiceStreamEvent.builder()
                    .type(VoiceStreamEvent.Type.ASR_RESULT)
                    .sessionId(sessionId.toString())
                    .messageId(messageId)
                    .timestamp(now.toEpochMilli())
                    .payload(Map.of("text", processed.userTranscription()))
                    .build());
        }

        events.add(VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.ROUND_COMPLETED)
                .sessionId(sessionId.toString())
                .messageId(messageId)
                .timestamp(now.toEpochMilli())
                .payload(Map.of("text", processed.aiResponse()))
                .build());

        events.add(VoiceStreamEvent.builder()
                .type(VoiceStreamEvent.Type.PROCESSING_COMPLETED)
                .sessionId(sessionId.toString())
                .messageId(messageId)
                .timestamp(now.toEpochMilli())
                .build());

        return events;
    }

    public record VoiceMessageWithMetadata(VoiceMessage voiceMessage, String messageId) {}
}
