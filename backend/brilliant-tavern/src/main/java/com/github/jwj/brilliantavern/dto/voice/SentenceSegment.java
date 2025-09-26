package com.github.jwj.brilliantavern.dto.voice;

import lombok.Builder;
import lombok.Value;

/**
 * 表示AI回复过程中的一句文本片段。
 */
@Value
@Builder
public class SentenceSegment {
    /** 消息ID，标识一轮对话 */
    String messageId;
    /** 段落自增序号，从0开始递增 */
    int order;
    /** 本段文本内容 */
    String text;
    /** 是否为本段的最终文本（通常用于最后一段） */
    boolean isFinal;
}
