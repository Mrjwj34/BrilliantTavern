package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.entity.CharacterCard;
import com.github.jwj.brilliantavern.service.tts.TTSConfig;
import com.github.jwj.brilliantavern.service.tts.TTSResponse;
import com.github.jwj.brilliantavern.service.tts.TTSService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * TTS管理服务
 * 负责协调不同的TTS服务实现，处理角色特定的音色配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TTSManagerService {

    private final TTSService ttsService;

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

        return ttsService.textToSpeech(text, voiceId)
                .map(audioData -> TTSResponse.builder()
                        .audioData(audioData)
                        .audioFormat(TTSConfig.AudioFormat.MP3)
                        .success(true)
                        .voiceId(voiceId)
                        .build())
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
