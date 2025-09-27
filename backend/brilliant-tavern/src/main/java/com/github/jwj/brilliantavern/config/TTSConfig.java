package com.github.jwj.brilliantavern.config;

import lombok.Data;

/**
 * TTS配置参数
 */
@Data
public class TTSConfig {
    
    /**
     * 音色ID
     */
    private String voiceId;
    
    /**
     * 语速（0.5-2.0）
     */
    private Double speed = 1.0;
    
    /**
     * 音调（0.5-2.0）
     */
    private Double pitch = 1.0;
    
    /**
     * 音量（0.0-1.0）
     */
    private Double volume = 1.0;
    
    /**
     * 音频格式
     */
    private AudioFormat audioFormat = AudioFormat.MP3;
    
    /**
     * 采样率
     */
    private Integer sampleRate = 44100;
    
    /**
     * 音频格式枚举
     */
    public enum AudioFormat {
        MP3, WAV, OGG, WEBM
    }
}
