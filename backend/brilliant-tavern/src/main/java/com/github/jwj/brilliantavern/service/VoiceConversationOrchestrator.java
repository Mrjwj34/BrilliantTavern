package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.dto.VoiceMessage;
import com.github.jwj.brilliantavern.dto.voice.VoiceStreamEvent;
import com.github.jwj.brilliantavern.entity.ChatHistory;
import com.github.jwj.brilliantavern.service.AIService.AIStreamEvent;
import com.github.jwj.brilliantavern.service.AIService.ProcessedAiResponse;
import com.github.jwj.brilliantavern.service.metrics.ConversationMetrics;
import com.github.jwj.brilliantavern.service.tts.TTSStreamChunk;
import com.github.jwj.brilliantavern.service.util.AsrMarkupProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.core.publisher.SynchronousSink;
import reactor.core.Disposable;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 协调语音输入、AI回复、TTS转换与消息推送的核心服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceConversationOrchestrator {

        private final AIService aiService;
        private final TTSManagerService ttsManagerService;
        private final VoiceChatService voiceChatService;
    public Flux<VoiceStreamEvent> processVoiceInput(UUID sessionId, VoiceMessageWithMetadata voiceMessage) {
        ConversationMetrics metrics = ConversationMetrics.start(sessionId.toString(), voiceMessage.messageId());
        VoiceChatService.SessionInfo sessionInfo = voiceChatService.getSession(sessionId);
        voiceChatService.extendSession(sessionId);

        String messageId = voiceMessage.messageId();
        String voiceIdRaw = sessionInfo.getCharacterCard().getTtsVoiceId();
        String voiceId = StringUtils.hasText(voiceIdRaw) ? voiceIdRaw : "default";

        AsrMarkupProcessor.StreamingProcessor asrProcessor = new AsrMarkupProcessor.StreamingProcessor();
        AtomicBoolean firstTextMarked = new AtomicBoolean(false);
        AtomicInteger finalSegmentOrder = new AtomicInteger(-1);
        AtomicInteger lastSegmentOrder = new AtomicInteger(-1);
        StringBuilder fullTextBuffer = new StringBuilder();
        AtomicReference<String> lastSegmentText = new AtomicReference<>("");

        Flux<AIStreamEvent> aiEvents = aiService.streamVoiceConversation(
                voiceMessage.voiceMessage(),
                sessionInfo.getCharacterCard(),
                sessionId.toString(),
                messageId
        ).doOnSubscribe(sub -> {
            metrics.mark("llm_start");
            log.debug("AI事件流订阅开始: sessionId={}, messageId={}", sessionId, messageId);
        }).doOnNext(event -> log.debug("收到AI事件: type={}, messageId={}, contentLength={}",
                event.getType(),
                event.getMessageId(),
                event.getContent() != null ? event.getContent().length() : 0))
         .doOnComplete(() -> log.debug("AI事件流完成: sessionId={}, messageId={}", sessionId, messageId))
         .doOnError(error -> log.error("AI事件流错误: sessionId={}, messageId={}", sessionId, messageId, error))
         .share();

                Flux<String> sanitizedTextFlux = aiEvents
                                .filter(event -> event.getType() == AIStreamEvent.Type.CHUNK)
                                .handle((AIStreamEvent event, SynchronousSink<String> sink) -> {
                                        String delta = asrProcessor.append(event.getContent());
                                        String normalized = AsrMarkupProcessor.normalizeWhitespace(delta);
                                        if (StringUtils.hasText(normalized)) {
                                                if (firstTextMarked.compareAndSet(false, true)) {
                                                        metrics.markIfAbsent("llm_first_token");
                                                }
                                                sink.next(normalized);
                                        }
                                })
                                .concatWith(Mono.defer(() -> {
                                        String remaining = AsrMarkupProcessor.normalizeWhitespace(asrProcessor.drain());
                                        return StringUtils.hasText(remaining) ? Mono.just(remaining) : Mono.empty();
                                }))
                                .filter(StringUtils::hasText);

                Flux<VoiceStreamEvent> textEvents = Flux.create(sink -> {
                        Disposable disposable = sanitizedTextFlux.subscribe(
                                        delta -> {
                                                fullTextBuffer.append(delta).append(' ');
                                                int segmentOrder = lastSegmentOrder.incrementAndGet();
                                                                        lastSegmentText.set(delta);
                                                                        sink.next(buildTextSegmentEvent(sessionId, messageId, segmentOrder, delta, false));
                                        },
                                        sink::error,
                                        () -> {
                                                int finalOrder = lastSegmentOrder.get();
                                                if (finalOrder >= 0) {
                                                        finalSegmentOrder.set(finalOrder);
                                                                                sink.next(buildTextSegmentEvent(sessionId, messageId, finalOrder, lastSegmentText.get(), true));
                                                }
                                                sink.complete();
                                        }
                        );
                        sink.onDispose(disposable::dispose);
                });

        Mono<ProcessedAiResponse> finalResponseMono = aiEvents
                .filter(event -> event.getType() == AIStreamEvent.Type.COMPLETED)
                .next()
                .map(AIStreamEvent::getProcessedResponse)
                .doOnNext(response -> metrics.mark("llm_completed"))
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

                        Flux<VoiceStreamEvent> completionEvents = finalResponseMono.flatMapMany(processed -> {
                        String aggregatedStreamText = AsrMarkupProcessor.normalizeWhitespace(fullTextBuffer.toString());
                        String streamSanitized = AsrMarkupProcessor.normalizeWhitespace(asrProcessor.getSanitizedText());
                        if (!StringUtils.hasText(streamSanitized)) {
                                streamSanitized = aggregatedStreamText;
                        }
                        String aiResponseText = StringUtils.hasText(processed.aiResponse()) ? processed.aiResponse() : streamSanitized;
                        if (!StringUtils.hasText(aiResponseText)) {
                                aiResponseText = aggregatedStreamText;
                        }
                            if (StringUtils.hasText(streamSanitized) && StringUtils.hasText(processed.aiResponse())
                                            && !processed.aiResponse().equals(streamSanitized)) {
                                String streamPreview = streamSanitized.length() > 200 ? streamSanitized.substring(0, 200) + "..." : streamSanitized;
                                String processedPreview = processed.aiResponse().length() > 200 ? processed.aiResponse().substring(0, 200) + "..." : processed.aiResponse();
                                log.debug("流式清洗文本与最终处理结果不一致: stream='{}', processed='{}'",
                                                streamPreview, processedPreview);
                        }

            String transcription = AsrMarkupProcessor.normalizeWhitespace(processed.userTranscription());

            persistHistory(sessionInfo, sessionId, processed)
                    .doOnSubscribe(sub -> metrics.mark("history_start"))
                    .doOnSuccess(unused -> metrics.mark("history_done"))
                    .doOnError(error -> log.error("保存对话历史失败: sessionId={}, messageId={}", sessionId, messageId, error))
                    .subscribe();

            int targetSegmentOrder = finalSegmentOrder.get() >= 0
                    ? finalSegmentOrder.get()
                    : Math.max(lastSegmentOrder.get(), 0);

            Flux<VoiceStreamEvent> asrEventFlux = StringUtils.hasText(transcription)
                    ? Flux.just(VoiceStreamEvent.builder()
                            .type(VoiceStreamEvent.Type.ASR_RESULT)
                            .sessionId(sessionId.toString())
                            .messageId(messageId)
                            .timestamp(Instant.now().toEpochMilli())
                            .payload(Map.of("text", transcription))
                            .build())
                    : Flux.empty();

                                        Flux<VoiceStreamEvent> textCorrectionFlux;
                                        if (StringUtils.hasText(aiResponseText)
                                                        && StringUtils.hasText(streamSanitized)
                                                        && !aiResponseText.equals(streamSanitized)) {
                                                lastSegmentText.set(aiResponseText);
                                                finalSegmentOrder.set(targetSegmentOrder);
                                                textCorrectionFlux = Flux.just(buildTextSegmentEvent(sessionId, messageId, targetSegmentOrder, aiResponseText, true));
                                        } else {
                                                textCorrectionFlux = Flux.empty();
                                        }

                            Flux<VoiceStreamEvent> ttsFlux = StringUtils.hasText(aiResponseText)
                                        ? ttsManagerService.streamSpeechWithVoice(aiResponseText, voiceId)
                                        .doOnSubscribe(sub -> metrics.mark("tts_start"))
                                        .doOnNext(chunk -> {
                                                if (chunk.getAudioData() != null && chunk.getAudioData().length > 0) {
                                                        metrics.markIfAbsent("tts_first_chunk");
                                                }
                                        })
                                        .doFinally(signal -> metrics.markIfAbsent("tts_completed"))
                                        .map(chunk -> buildAudioChunkEvent(sessionId, messageId, targetSegmentOrder, chunk))
                                        .onErrorResume(error -> {
                                                metrics.markIfAbsent("tts_completed");
                                                log.error("TTS生成失败: sessionId={}, messageId={}", sessionId, messageId, error);
                                                return Flux.just(VoiceStreamEvent.builder()
                                                                .type(VoiceStreamEvent.Type.ERROR)
                                                                .sessionId(sessionId.toString())
                                                                .messageId(messageId)
                                                                .timestamp(Instant.now().toEpochMilli())
                                                                .payload(Map.of("error", "TTS生成失败: " + error.getMessage()))
                                                                .build());
                                        })
                                        : Flux.<VoiceStreamEvent>empty();

            VoiceStreamEvent roundCompleted = VoiceStreamEvent.builder()
                    .type(VoiceStreamEvent.Type.ROUND_COMPLETED)
                    .sessionId(sessionId.toString())
                    .messageId(messageId)
                    .timestamp(Instant.now().toEpochMilli())
                    .payload(Map.of("text", aiResponseText))
                    .build();

            VoiceStreamEvent processingCompleted = VoiceStreamEvent.builder()
                    .type(VoiceStreamEvent.Type.PROCESSING_COMPLETED)
                    .sessionId(sessionId.toString())
                    .messageId(messageId)
                    .timestamp(Instant.now().toEpochMilli())
                    .build();

            return asrEventFlux
                    .concatWith(textCorrectionFlux)
                    .concatWith(ttsFlux)
                    .concatWith(Flux.just(roundCompleted, processingCompleted));
        });

        Flux<VoiceStreamEvent> mergedEvents = Flux.merge(textEvents, completionEvents);

        return Flux.concat(processingStarted, mergedEvents)
                .onErrorResume(error -> {
                    log.error("语音对话处理失败: sessionId={}, messageId={}", sessionId, messageId, error);
                    return Flux.just(VoiceStreamEvent.builder()
                            .type(VoiceStreamEvent.Type.ERROR)
                            .sessionId(sessionId.toString())
                            .messageId(messageId)
                            .timestamp(Instant.now().toEpochMilli())
                            .payload(Map.of("error", error.getMessage()))
                            .build());
                })
                .doFinally(signalType -> {
                    metrics.mark("flow_completed");
                    String report = metrics.buildReport();
                    if (StringUtils.hasText(report)) {
                        log.info(report);
                    }
                });
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

        private VoiceStreamEvent buildTextSegmentEvent(UUID sessionId,
                                                                                                   String messageId,
                                                                                                   int segmentOrder,
                                                                                                   String text,
                                                                                                   boolean isFinal) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("segmentOrder", segmentOrder);
                payload.put("text", text);
                payload.put("isFinal", isFinal);

                return VoiceStreamEvent.builder()
                                .type(VoiceStreamEvent.Type.AI_TEXT_SEGMENT)
                                .sessionId(sessionId.toString())
                                .messageId(messageId)
                                .timestamp(Instant.now().toEpochMilli())
                                .payload(payload)
                                .build();
        }

        private VoiceStreamEvent buildAudioChunkEvent(UUID sessionId,
                                                     String messageId,
                                                     int segmentOrder,
                                                     TTSStreamChunk chunk) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("segmentOrder", segmentOrder);
                payload.put("chunkIndex", chunk.getChunkIndex());
                payload.put("isLast", chunk.isLast());
                payload.put("audioFormat", chunk.getAudioFormat() != null
                                ? chunk.getAudioFormat().name().toLowerCase()
                                : "wav");
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
                                .sessionId(sessionId.toString())
                                .messageId(messageId)
                                .timestamp(Instant.now().toEpochMilli())
                                .payload(payload)
                                .build();
        }

    public record VoiceMessageWithMetadata(VoiceMessage voiceMessage, String messageId) {}
}
