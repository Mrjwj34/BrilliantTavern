package com.github.jwj.brilliantavern.service.tts;

import lombok.Builder;
import lombok.Value;

/**
 * 表示TTS流式生成的音频片段。
 */
@Value
@Builder
public class TtsChunk {

    /** 当前片段的序号，从0开始递增 */
    int chunkIndex;

    /** 实际音频数据字节 */
    byte[] audioData;

    /** 是否为该段的最后一个片段 */
    boolean last;

    /** 音频格式（如mp3、wav等） */
    String audioFormat;

    public static TtsChunk of(int index, byte[] audioData, boolean last, String audioFormat) {
        return TtsChunk.builder()
                .chunkIndex(index)
                .audioData(audioData)
                .last(last)
                .audioFormat(audioFormat)
                .build();
    }
}
