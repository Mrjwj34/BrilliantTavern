package com.github.jwj.brilliantavern.service.tts.impl;

import com.github.jwj.brilliantavern.service.tts.TTSService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 模拟TTS服务实现（开发阶段使用）
 * 生成模拟的音频数据，便于开发测试
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.tts.provider", havingValue = "mock", matchIfMissing = true)
public class MockTTSService implements TTSService {

    private static final String[] MOCK_VOICES = {
        "voice-001", "voice-002", "voice-003"
    };

    @Override
    public Mono<byte[]> textToSpeech(String text, String voiceId) {
        log.info("模拟TTS转换: 文本='{}', 音色='{}'", text, voiceId);
        
        // 模拟网络延迟
        return Mono.delay(Duration.ofMillis(500))
                .map(delay -> {
                    // 生成模拟的音频数据
                    String mockAudioData = String.format("MOCK_AUDIO_DATA[voice=%s,text=%s]", 
                            voiceId != null ? voiceId : "default", text);
                    return mockAudioData.getBytes(StandardCharsets.UTF_8);
                })
                .doOnNext(audioData -> log.debug("生成模拟音频数据，大小: {} 字节", audioData.length))
                .doOnError(error -> log.error("模拟TTS转换失败", error));
    }

    @Override
    public Mono<String[]> getSupportedVoices() {
        log.debug("获取支持的音色列表");
        return Mono.just(MOCK_VOICES);
    }

    @Override
    public Mono<Boolean> isServiceAvailable() {
        log.debug("检查TTS服务可用性");
        return Mono.just(true);
    }
}
