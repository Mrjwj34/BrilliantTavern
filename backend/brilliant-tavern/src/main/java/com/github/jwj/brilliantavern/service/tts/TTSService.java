package com.github.jwj.brilliantavern.service.tts;

import org.springframework.stereotype.Service;
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
     * 将文本转换为语音音频数据（使用默认音色）
     * 
     * @param text 要转换的文本
     * @return 音频数据的Mono流
     */
    default Mono<byte[]> textToSpeech(String text) {
        return textToSpeech(text, null);
    }

    /**
     * 将文本转换为流式语音分片，默认实现回退到整体生成。
     *
     * @param text 要转换的文本
     * @param voiceId 音色ID
     * @return 音频分片流
     */
    default Flux<TTSStreamChunk> streamTextToSpeech(String text, String voiceId) {
        return textToSpeech(text, voiceId)
                .flatMapMany(audio -> Flux.just(TTSStreamChunk.builder()
                        .chunkIndex(0)
                        .audioData(audio)
                        .audioFormat(null)
                        .last(true)
                        .build()));
    }

    default Flux<TTSStreamChunk> streamTextToSpeech(String text) {
        return streamTextToSpeech(text, null);
    }

}
