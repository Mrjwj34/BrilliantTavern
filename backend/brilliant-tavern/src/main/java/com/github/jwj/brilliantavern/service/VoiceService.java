package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.dto.VoiceOption;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 语音服务 - 占位符实现
 */
@Service
public class VoiceService {

    /**
     * 获取可用的语音列表
     * 目前返回模拟数据，后续会接入真实的语音API
     */
    public List<VoiceOption> getAvailableVoices() {
        return Arrays.asList(
            VoiceOption.builder()
                .id("voice_001")
                .name("小雨")
                .description("温柔甜美的女声")
                .type("female")
                .language("zh-CN")
                .build(),
            VoiceOption.builder()
                .id("voice_002")
                .name("小明")
                .description("阳光活泼的男声")
                .type("male")
                .language("zh-CN")
                .build(),
            VoiceOption.builder()
                .id("voice_003")
                .name("小慧")
                .description("知性优雅的女声")
                .type("female")
                .language("zh-CN")
                .build(),
            VoiceOption.builder()
                .id("voice_004")
                .name("小峰")
                .description("成熟稳重的男声")
                .type("male")
                .language("zh-CN")
                .build(),
            VoiceOption.builder()
                .id("voice_005")
                .name("小萌")
                .description("可爱活泼的女声")
                .type("female")
                .language("zh-CN")
                .build()
        );
    }

    /**
     * 根据ID获取语音选项
     */
    public VoiceOption getVoiceById(String voiceId) {
        return getAvailableVoices().stream()
            .filter(voice -> voice.getId().equals(voiceId))
            .findFirst()
            .orElse(null);
    }

    /**
     * 验证语音ID是否有效
     */
    public boolean isValidVoiceId(String voiceId) {
        return getVoiceById(voiceId) != null;
    }
}
