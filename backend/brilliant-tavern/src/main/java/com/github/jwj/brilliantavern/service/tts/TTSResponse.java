package com.github.jwj.brilliantavern.service.tts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TTS响应结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TTSResponse {
    
    /**
     * 音频数据
     */
    private byte[] audioData;
    
    /**
     * 音频格式
     */
    private TTSConfig.AudioFormat audioFormat;
    
    /**
     * 生成成功标识
     */
    private Boolean success;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 使用的音色ID
     */
    private String voiceId;
}
