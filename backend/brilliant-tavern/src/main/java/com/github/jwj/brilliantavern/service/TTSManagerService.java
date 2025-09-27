package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.config.TTSConfig;
import com.github.jwj.brilliantavern.dto.TTSResponse;
import com.github.jwj.brilliantavern.service.tts.TTSService;
import com.github.jwj.brilliantavern.service.tts.TTSStreamChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;

/**
 * TTS管理服务
 * 负责协调不同的TTS服务实现，处理角色特定的音色配置，并支持缓存功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TTSManagerService {

    private final TTSService ttsService;
    private final TTSCacheService ttsCacheService;
    private final com.github.jwj.brilliantavern.service.streaming.RetryService retryService;

    /**
     * 流式生成语音（指定音色），支持重试
     */
    public Flux<TTSStreamChunk> streamSpeechWithVoice(String text, String voiceId, String sessionId, String messageId) {
        if (text == null || text.trim().isEmpty()) {
            return Flux.error(new IllegalArgumentException("文本内容为空"));
        }

        final boolean isTestText = ttsCacheService.isTestText(text);
        if (isTestText) {
            byte[] cachedAudio = ttsCacheService.getCachedTestAudio(text, voiceId);
            if (cachedAudio != null) {
                return Flux.just(TTSStreamChunk.builder()
                        .chunkIndex(0)
                        .audioData(cachedAudio)
                        .audioFormat(TTSConfig.AudioFormat.MP3)
                        .last(true)
                        .fromCache(true)
                        .build());
            }
        }

        return retryService.retryWithProgress(
                createTTSStream(text, voiceId, isTestText),
                sessionId,
                messageId,
                "TTS调用",
                context -> Flux.just(retryService.createRetryProgressEvent(context))
        );
    }
    
    /**
     * 创建TTS流 - 单次调用逻辑
     */
    private Flux<TTSStreamChunk> createTTSStream(String text, String voiceId, boolean isTestText) {
        return Flux.defer(() -> {
            ByteArrayOutputStream cacheCollector = new ByteArrayOutputStream();

            return ttsService.streamTextToSpeech(text, voiceId)
                    .publishOn(Schedulers.boundedElastic())
                    .map(chunk -> {
                        if (isTestText && chunk.getAudioData() != null && chunk.getAudioData().length > 0) {
                            try {
                                cacheCollector.write(chunk.getAudioData());
                            } catch (Exception e) {
                                log.warn("缓存测试音频失败", e);
                            }
                        }
                        return chunk;
                    })
                    .doOnError(error -> log.error("流式语音生成失败，音色: {}, 错误: {}", voiceId, error.getMessage(), error))
                    .doOnComplete(() -> {
                        if (isTestText && cacheCollector.size() > 0) {
                            ttsCacheService.putCachedTestAudio(text, voiceId, cacheCollector.toByteArray());
                        }
                    });
        });
    }
    
    /**
     * 流式生成语音（指定音色） - 向后兼容方法
     */
    public Flux<TTSStreamChunk> streamSpeechWithVoice(String text, String voiceId) {
        return streamSpeechWithVoice(text, voiceId, "unknown", "unknown");
    }

    /**
     * 生成语音（使用默认音色）
     * 
     * @param text 要转换的文本
     * @return TTS响应结果
     */
    public Mono<TTSResponse> generateSpeechWithDefaultVoice(String text) {
        return generateSpeechWithVoice(text, null);
    }

    /**
     * 生成语音（指定音色）
     * 对测试文本启用缓存功能
     * 
     * @param text 要转换的文本
     * @param voiceId 音色ID
     * @return TTS响应结果
     */
    public Mono<TTSResponse> generateSpeechWithVoice(String text, String voiceId) {
        if (text == null || text.trim().isEmpty()) {
            return Mono.just(TTSResponse.builder()
                    .success(false)
                    .errorMessage("文本内容为空")
                    .build());
        }

        // 如果是测试文本，尝试从缓存获取
        if (ttsCacheService.isTestText(text)) {
            byte[] cachedAudio = ttsCacheService.getCachedTestAudio(text, voiceId);
            if (cachedAudio != null) {
                log.debug("从缓存返回测试音频: voiceId={}", voiceId);
                return Mono.just(TTSResponse.builder()
                        .audioData(cachedAudio)
                        .audioFormat(TTSConfig.AudioFormat.MP3)
                        .success(true)
                        .voiceId(voiceId)
                        .fromCache(true)
                        .build());
            }
        }

        // 缓存未命中或非测试文本，调用TTS服务
        return ttsService.textToSpeech(text, voiceId)
                .<TTSResponse>map(audioData -> {
                    // 如果是测试文本，将生成的音频缓存起来
                    if (ttsCacheService.isTestText(text)) {
                        ttsCacheService.putCachedTestAudio(text, voiceId, audioData);
                    }
                    
                    return TTSResponse.builder()
                            .audioData(audioData)
                            .audioFormat(TTSConfig.AudioFormat.MP3)
                            .success(true)
                            .voiceId(voiceId)
                            .fromCache(false)
                            .build();
                })
                .onErrorResume(error -> {
                    log.error("语音生成失败，音色: {}, 错误: {}", voiceId, error.getMessage());
                    return Mono.just(TTSResponse.builder()
                            .success(false)
                            .errorMessage(error.getMessage())
                            .voiceId(voiceId)
                            .build());
                });
    }

}
