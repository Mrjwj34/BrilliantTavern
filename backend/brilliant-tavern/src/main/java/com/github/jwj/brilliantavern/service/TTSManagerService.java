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
     * 为指定角色生成语音
     * 
     * @param text 要转换的文本
     * @param characterCard 角色卡信息（包含音色配置）
     * @return TTS响应结果
     */
    public Mono<TTSResponse> generateSpeech(String text, CharacterCard characterCard) {
        if (text == null || text.trim().isEmpty()) {
            return Mono.just(TTSResponse.builder()
                    .success(false)
                    .errorMessage("文本内容为空")
                    .build());
        }

        // 获取角色的音色配置
        String voiceId = getCharacterVoiceId(characterCard);
        
        log.debug("为角色 '{}' 生成语音，使用音色: '{}'", characterCard.getName(), voiceId);

        return ttsService.textToSpeech(text, voiceId)
                .map(audioData -> TTSResponse.builder()
                        .audioData(audioData)
                        .audioFormat(TTSConfig.AudioFormat.MP3)
                        .success(true)
                        .voiceId(voiceId)
                        .duration(estimateAudioDuration(text))
                        .sampleRate(22050)
                        .build())
                .doOnSuccess(response -> log.debug("语音生成成功，音频大小: {} 字节", 
                        response.getAudioData() != null ? response.getAudioData().length : 0))
                .onErrorResume(error -> {
                    log.error("语音生成失败，角色: {}, 错误: {}", characterCard.getName(), error.getMessage());
                    return Mono.just(TTSResponse.builder()
                            .success(false)
                            .errorMessage(error.getMessage())
                            .voiceId(voiceId)
                            .build());
                });
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
                        .duration(estimateAudioDuration(text))
                        .sampleRate(22050)
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

    /**
     * 获取支持的音色列表
     * 
     * @return 音色ID数组
     */
    public Mono<String[]> getSupportedVoices() {
        return ttsService.getSupportedVoices();
    }

    /**
     * 检查TTS服务可用性
     * 
     * @return 服务状态
     */
    public Mono<Boolean> checkServiceStatus() {
        return ttsService.isServiceAvailable();
    }

    /**
     * 获取角色的音色ID
     * 
     * @param characterCard 角色卡
     * @return 音色ID
     */
    private String getCharacterVoiceId(CharacterCard characterCard) {
        if (characterCard != null && characterCard.getTtsVoiceId() != null) {
            return characterCard.getTtsVoiceId();
        }
        // 返回默认音色
        return "voice-001";
    }

    /**
     * 估算音频时长（基于文本长度的简单估算）
     * 
     * @param text 文本内容
     * @return 估算的时长（秒）
     */
    private Double estimateAudioDuration(String text) {
        // 简单估算：平均每分钟150字，每秒2.5字
        int charCount = text.length();
        return charCount / 2.5;
    }
}
