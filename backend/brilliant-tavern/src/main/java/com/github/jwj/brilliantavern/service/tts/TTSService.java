package com.github.jwj.brilliantavern.service.tts;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * TTS服务接口，用于将文本转换为语音
 */
public interface TTSService {

    /**
     * 将文本转换为语音音频数据
     * 
     * @param text 要转换的文本
     * @param voiceId 音色ID（如果支持）
     * @return 音频数据的Mono流
     */
    Mono<byte[]> textToSpeech(String text, String voiceId);

    /**
     * 以流式方式生成语音。
     */
    default Flux<TtsChunk> streamTextToSpeech(String text, String voiceId) {
        return textToSpeech(text, voiceId)
                .flatMapMany(bytes -> {
                    if (bytes == null || bytes.length == 0) {
                        return Flux.empty();
                    }
                    return Flux.just(TtsChunk.of(0, bytes, true, null));
                });
    }

    /**
     * 将文本转换为语音音频数据（使用默认音色）
     * 
     * @param text 要转换的文本
     * @return 音频数据的Mono流
     */
    default Mono<byte[]> textToSpeech(String text) {
        return textToSpeech(text, null);
    }

    /**
     * 默认的流式实现可基于非流式方法实现。
     */
    default Flux<TtsChunk> streamTextToSpeech(String text) {
        return streamTextToSpeech(text, null);
    }

}
