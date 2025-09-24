package com.github.jwj.brilliantavern.service.tts;

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
     * 获取支持的音色列表
     * 
     * @return 音色ID列表
     */
    Mono<String[]> getSupportedVoices();

    /**
     * 检查TTS服务是否可用
     * 
     * @return 服务状态
     */
    Mono<Boolean> isServiceAvailable();
}
