package com.github.jwj.brilliantavern.service.streaming;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 标签事件定义
 * 表示在流式内容中检测到的各种标签事件
 */
@Data
@Builder
public class TagEvent {
    
    /**
     * 标签类型枚举
     */
    public enum TagType {
        TSS,    // [TSS:lan] TTS服务文本
        SUB,    // [SUB:lan] 字幕文本  
        ASR,    // [ASR] 用户转写内容
        DO      // [DO] 方法执行区域
    }
    
    /**
     * 事件类型枚举
     */
    public enum EventType {
        TAG_OPENED,     // 标签开始
        CONTENT_CHUNK,  // 内容片段
        TAG_CLOSED      // 标签结束
    }
    
    private TagType tagType;
    private EventType eventType;
    private String language;        // 语言代码，如 "zh", "en"
    private String content;         // 标签内容
    private String sessionId;
    private String messageId;
    private Instant timestamp;
    private int position;           // 在流中的位置
    
    /**
     * 创建TSS标签开始事件
     */
    public static TagEvent tssOpened(String language, String sessionId, String messageId, int position) {
        return TagEvent.builder()
                .tagType(TagType.TSS)
                .eventType(EventType.TAG_OPENED)
                .language(language)
                .sessionId(sessionId)
                .messageId(messageId)
                .position(position)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 创建TSS内容片段事件
     */
    public static TagEvent tssContent(String content, String sessionId, String messageId, int position) {
        return TagEvent.builder()
                .tagType(TagType.TSS)
                .eventType(EventType.CONTENT_CHUNK)
                .content(content)
                .sessionId(sessionId)
                .messageId(messageId)
                .position(position)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 创建TSS标签结束事件
     */
    public static TagEvent tssClosed(String sessionId, String messageId, int position) {
        return TagEvent.builder()
                .tagType(TagType.TSS)
                .eventType(EventType.TAG_CLOSED)
                .sessionId(sessionId)
                .messageId(messageId)
                .position(position)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 创建SUB标签开始事件
     */
    public static TagEvent subOpened(String language, String sessionId, String messageId, int position) {
        return TagEvent.builder()
                .tagType(TagType.SUB)
                .eventType(EventType.TAG_OPENED)
                .language(language)
                .sessionId(sessionId)
                .messageId(messageId)
                .position(position)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 创建SUB内容片段事件
     */
    public static TagEvent subContent(String content, String sessionId, String messageId, int position) {
        return TagEvent.builder()
                .tagType(TagType.SUB)
                .eventType(EventType.CONTENT_CHUNK)
                .content(content)
                .sessionId(sessionId)
                .messageId(messageId)
                .position(position)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 创建SUB标签结束事件
     */
    public static TagEvent subClosed(String sessionId, String messageId, int position) {
        return TagEvent.builder()
                .tagType(TagType.SUB)
                .eventType(EventType.TAG_CLOSED)
                .sessionId(sessionId)
                .messageId(messageId)
                .position(position)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 创建ASR标签开始事件
     */
    public static TagEvent asrOpened(String sessionId, String messageId, int position) {
        return TagEvent.builder()
                .tagType(TagType.ASR)
                .eventType(EventType.TAG_OPENED)
                .sessionId(sessionId)
                .messageId(messageId)
                .position(position)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 创建ASR内容片段事件
     */
    public static TagEvent asrContent(String content, String sessionId, String messageId, int position) {
        return TagEvent.builder()
                .tagType(TagType.ASR)
                .eventType(EventType.CONTENT_CHUNK)
                .content(content)
                .sessionId(sessionId)
                .messageId(messageId)
                .position(position)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 创建ASR标签结束事件
     */
    public static TagEvent asrClosed(String sessionId, String messageId, int position) {
        return TagEvent.builder()
                .tagType(TagType.ASR)
                .eventType(EventType.TAG_CLOSED)
                .sessionId(sessionId)
                .messageId(messageId)
                .position(position)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 创建DO标签开始事件
     */
    public static TagEvent doOpened(String sessionId, String messageId, int position) {
        return TagEvent.builder()
                .tagType(TagType.DO)
                .eventType(EventType.TAG_OPENED)
                .sessionId(sessionId)
                .messageId(messageId)
                .position(position)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 创建DO内容片段事件
     */
    public static TagEvent doContent(String content, String sessionId, String messageId, int position) {
        return TagEvent.builder()
                .tagType(TagType.DO)
                .eventType(EventType.CONTENT_CHUNK)
                .content(content)
                .sessionId(sessionId)
                .messageId(messageId)
                .position(position)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * 创建DO标签结束事件
     */
    public static TagEvent doClosed(String sessionId, String messageId, int position) {
        return TagEvent.builder()
                .tagType(TagType.DO)
                .eventType(EventType.TAG_CLOSED)
                .sessionId(sessionId)
                .messageId(messageId)
                .position(position)
                .timestamp(Instant.now())
                .build();
    }
}