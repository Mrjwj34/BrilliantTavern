package com.github.jwj.brilliantavern.service.tts;

import com.github.jwj.brilliantavern.config.TTSConfig;
import lombok.Builder;
import lombok.Value;

/**
 * 表示TTS服务返回的流式音频分片。
 */
@Value
@Builder
public class TTSStreamChunk {

    /** 分片顺序，从0开始 */
    int chunkIndex;

    /** 分片音频字节数据 */
    byte[] audioData;

    /** 音频格式 */
    TTSConfig.AudioFormat audioFormat;

    /** 采样率（Hz），若未知则为null */
    Integer sampleRate;

    /** 声道数，若未知则为null */
    Integer channels;

    /** 每个采样的位宽，若未知则为null */
    Integer bitsPerSample;

    /** 当前分片是否为该段音频的最后一个分片 */
    boolean last;

    /** 是否命中缓存 */
    @Builder.Default
    boolean fromCache = false;
}
