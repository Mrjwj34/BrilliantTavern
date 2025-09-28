package com.github.jwj.brilliantavern.service.streaming;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流式内容解析器
 * 基于状态机实现实时标签检测和事件触发
 */
@Slf4j
@Component
public class StreamingContentParser {
    
    // 标签模式
    private static final Pattern TSS_OPEN_PATTERN = Pattern.compile("\\[TSS:([a-z]{2})]");
    private static final Pattern TSS_CLOSE_PATTERN = Pattern.compile("\\[/TSS]");
    private static final Pattern SUB_OPEN_PATTERN = Pattern.compile("\\[SUB:([a-z]{2})]");
    private static final Pattern SUB_CLOSE_PATTERN = Pattern.compile("\\[/SUB]");
    private static final Pattern ASR_OPEN_PATTERN = Pattern.compile("\\[ASR]");
    private static final Pattern ASR_CLOSE_PATTERN = Pattern.compile("\\[/ASR]");
    private static final Pattern DO_OPEN_PATTERN = Pattern.compile("\\[DO]");
    private static final Pattern DO_CLOSE_PATTERN = Pattern.compile("\\[/DO]");
    
    /**
     * 解析器状态
     */
    private enum ParserState {
        NORMAL,         // 正常文本
        IN_TSS_TAG,     // TSS标签内
        IN_SUB_TAG,     // SUB标签内
        IN_ASR_TAG,     // ASR标签内
        IN_DO_TAG       // DO标签内
    }
    
    /**
     * 解析器上下文
     */
    private static class ParserContext {
        ParserState state = ParserState.NORMAL;
        StringBuilder buffer = new StringBuilder();
        String currentLanguage = null;
        String sessionId;
        String messageId;
        AtomicInteger position = new AtomicInteger(0);
        FluxSink<TagEvent> sink;
        boolean hasValidTags = false; // 跟踪是否遇到有效标签
        StringBuilder fullContent = new StringBuilder(); // 用于错误诊断
        
        ParserContext(String sessionId, String messageId, FluxSink<TagEvent> sink) {
            this.sessionId = sessionId;
            this.messageId = messageId;
            this.sink = sink;
        }
    }
    
    /**
     * 解析流式内容并生成标签事件
     */
    public Flux<TagEvent> parseStream(Flux<String> contentStream, String sessionId, String messageId) {
        return Flux.<TagEvent>create(sink -> {
            ParserContext context = new ParserContext(sessionId, messageId, sink);
            
            contentStream.subscribe(
                chunk -> processChunk(chunk, context),
                error -> {
                    log.error("流式解析过程中发生错误", error);
                    sink.error(error);
                },
                () -> {
                    // 处理结束时的缓冲区内容
                    flushBuffer(context);
                    
                    // 检查是否解析到有效标签，但[MEM]标签请求例外
                    if (!context.hasValidTags) {
                        String fullContent = context.fullContent.toString();
                        
                        // 如果是[MEM]标签请求，跳过标签格式验证
                        if (isSingleMemTagContent(fullContent)) {
                            log.debug("检测到[MEM]标签请求，跳过标签格式验证: sessionId={}, messageId={}", 
                                    context.sessionId, context.messageId);
                            sink.complete();
                            return;
                        }
                        
                        String errorMsg = String.format("AI响应缺少必需的标签格式。完整内容: %s", fullContent);
                        log.warn("标签解析失败: sessionId={}, messageId={}, content={}", 
                                context.sessionId, context.messageId, fullContent);
                        sink.error(new TagParsingException(errorMsg, fullContent));
                        return;
                    }
                    
                    sink.complete();
                }
            );
        }).doOnSubscribe(sub -> log.debug("开始流式标签解析: sessionId={}, messageId={}", sessionId, messageId))
          .doOnComplete(() -> log.debug("流式标签解析完成: sessionId={}, messageId={}", sessionId, messageId));
    }
    
    /**
     * 处理内容块
     */
    private void processChunk(String chunk, ParserContext context) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        
        // 收集完整内容用于错误诊断
        context.fullContent.append(chunk);
        
        context.buffer.append(chunk);
        String bufferContent = context.buffer.toString();
        
        // 查找并处理标签
        int processed = 0;
        while (processed < bufferContent.length()) {
            TagMatch match = findNextTag(bufferContent, processed);
            
            if (match == null) {
                // 没有找到更多标签，不处理剩余内容，等待更多数据
                // 保留未完全匹配的内容在缓冲区
                String remaining = bufferContent.substring(processed);
                context.buffer.setLength(0);
                context.buffer.append(remaining);
                break;
            }
            
            // 处理标签前的内容
            if (match.start > processed) {
                String beforeTag = bufferContent.substring(processed, match.start);
                processContent(beforeTag, context);
            }
            
            // 处理标签
            processTag(match, context);
            processed = match.end;
        }
        
        // 更新缓冲区，保留未处理的内容
        if (processed > 0 && processed < bufferContent.length()) {
            String remaining = bufferContent.substring(processed);
            context.buffer.setLength(0);
            context.buffer.append(remaining);
        } else if (processed >= bufferContent.length()) {
            // 所有内容都已处理
            context.buffer.setLength(0);
        }
    }
    
    /**
     * 查找下一个标签
     */
    private TagMatch findNextTag(String content, int start) {
        TagMatch earliest = null;
        
        // 检查各种标签模式
        TagMatch[] candidates = {
            findPattern(TSS_OPEN_PATTERN, content, start, TagEvent.TagType.TSS, true),
            findPattern(TSS_CLOSE_PATTERN, content, start, TagEvent.TagType.TSS, false),
            findPattern(SUB_OPEN_PATTERN, content, start, TagEvent.TagType.SUB, true),
            findPattern(SUB_CLOSE_PATTERN, content, start, TagEvent.TagType.SUB, false),
            findPattern(ASR_OPEN_PATTERN, content, start, TagEvent.TagType.ASR, true),
            findPattern(ASR_CLOSE_PATTERN, content, start, TagEvent.TagType.ASR, false),
            findPattern(DO_OPEN_PATTERN, content, start, TagEvent.TagType.DO, true),
            findPattern(DO_CLOSE_PATTERN, content, start, TagEvent.TagType.DO, false)
        };
        
        for (TagMatch candidate : candidates) {
            if (candidate != null && (earliest == null || candidate.start < earliest.start)) {
                earliest = candidate;
            }
        }
        
        return earliest;
    }
    
    /**
     * 查找模式匹配
     */
    private TagMatch findPattern(Pattern pattern, String content, int start, TagEvent.TagType tagType, boolean isOpen) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find(start)) {
            String language = null;
            if (matcher.groupCount() > 0) {
                language = matcher.group(1);
            }
            return new TagMatch(matcher.start(), matcher.end(), tagType, isOpen, language);
        }
        return null;
    }
    
    /**
     * 处理标签
     */
    private void processTag(TagMatch match, ParserContext context) {
        int currentPos = context.position.getAndAdd(match.end - match.start);
        
        if (match.isOpen) {
            // 开始标签
            handleTagOpen(match.tagType, match.language, context, currentPos);
        } else {
            // 结束标签
            handleTagClose(match.tagType, context, currentPos);
        }
    }
    
    /**
     * 处理标签开始
     */
    private void handleTagOpen(TagEvent.TagType tagType, String language, ParserContext context, int position) {
        switch (tagType) {
            case TSS:
                if (context.state == ParserState.NORMAL) {
                    context.state = ParserState.IN_TSS_TAG;
                    context.currentLanguage = language;
                    context.hasValidTags = true;
                    context.sink.next(TagEvent.tssOpened(language, context.sessionId, context.messageId, position));
                }
                break;
            case SUB:
                if (context.state == ParserState.NORMAL) {
                    context.state = ParserState.IN_SUB_TAG;
                    context.currentLanguage = language;
                    context.hasValidTags = true;
                    context.sink.next(TagEvent.subOpened(language, context.sessionId, context.messageId, position));
                }
                break;
            case ASR:
                if (context.state == ParserState.NORMAL) {
                    context.state = ParserState.IN_ASR_TAG;
                    context.hasValidTags = true;
                    context.sink.next(TagEvent.asrOpened(context.sessionId, context.messageId, position));
                }
                break;
            case DO:
                if (context.state == ParserState.NORMAL) {
                    context.state = ParserState.IN_DO_TAG;
                    context.hasValidTags = true;
                    context.sink.next(TagEvent.doOpened(context.sessionId, context.messageId, position));
                }
                break;
        }
    }
    
    /**
     * 处理标签结束
     */
    private void handleTagClose(TagEvent.TagType tagType, ParserContext context, int position) {
        if (isCorrectCloseTag(tagType, context.state)) {
            context.state = ParserState.NORMAL;
            context.currentLanguage = null;
            
            switch (tagType) {
                case TSS:
                    context.sink.next(TagEvent.tssClosed(context.sessionId, context.messageId, position));
                    break;
                case SUB:
                    context.sink.next(TagEvent.subClosed(context.sessionId, context.messageId, position));
                    break;
                case ASR:
                    context.sink.next(TagEvent.asrClosed(context.sessionId, context.messageId, position));
                    break;
                case DO:
                    context.sink.next(TagEvent.doClosed(context.sessionId, context.messageId, position));
                    break;
            }
        }
    }
    
    /**
     * 检查是否为正确的结束标签
     */
    private boolean isCorrectCloseTag(TagEvent.TagType tagType, ParserState state) {
        return switch (tagType) {
            case TSS -> state == ParserState.IN_TSS_TAG;
            case SUB -> state == ParserState.IN_SUB_TAG;
            case ASR -> state == ParserState.IN_ASR_TAG;
            case DO -> state == ParserState.IN_DO_TAG;
        };
    }
    
    /**
     * 处理内容
     */
    private void processContent(String content, ParserContext context) {
        if (content.isEmpty()) {
            return;
        }
        
        int currentPos = context.position.getAndAdd(content.length());
        
        switch (context.state) {
            case IN_TSS_TAG:
                context.sink.next(TagEvent.tssContent(content, context.sessionId, context.messageId, currentPos));
                break;
            case IN_SUB_TAG:
                context.sink.next(TagEvent.subContent(content, context.sessionId, context.messageId, currentPos));
                break;
            case IN_ASR_TAG:
                context.sink.next(TagEvent.asrContent(content, context.sessionId, context.messageId, currentPos));
                break;
            case IN_DO_TAG:
                context.sink.next(TagEvent.doContent(content, context.sessionId, context.messageId, currentPos));
                break;
            case NORMAL:
                // 正常内容不生成事件
                break;
        }
    }
    
    /**
     * 清空缓冲区
     */
    private void flushBuffer(ParserContext context) {
        if (!context.buffer.isEmpty()) {
            processContent(context.buffer.toString(), context);
            context.buffer.setLength(0);
        }
    }
    
    /**
     * 标签匹配结果
     */
    private static class TagMatch {
        final int start;
        final int end;
        final TagEvent.TagType tagType;
        final boolean isOpen;
        final String language;
        
        TagMatch(int start, int end, TagEvent.TagType tagType, boolean isOpen, String language) {
            this.start = start;
            this.end = end;
            this.tagType = tagType;
            this.isOpen = isOpen;
            this.language = language;
        }
    }
    
    /**
     * 检测是否为单个[MEM]标签内容
     */
    private boolean isSingleMemTagContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = content.trim();
        
        // 检查是否以[MEM]开头且包含结束标签
        if (trimmed.startsWith("[MEM]") && trimmed.contains("[/MEM]")) {
            // 确保不包含其他标准标签 (TSS, SUB, ASR, DO)
            boolean hasOtherTags = trimmed.contains("[TSS:") || 
                                 trimmed.contains("[SUB:") || 
                                 trimmed.contains("[ASR]") ||
                                 trimmed.contains("[DO]");
            
            // 检测是否有重复的[MEM]标签（表示格式错误）
            int memCount = (trimmed.length() - trimmed.replace("[MEM]", "").length()) / 5; // "[MEM]"长度为5
            boolean hasRepeatedMemTags = memCount > 1;
            
            return !hasOtherTags && !hasRepeatedMemTags;
        }
        
        return false;
    }
    
    /**
     * 标签解析异常
     */
    @Getter
    public static class TagParsingException extends RuntimeException {
        private final String fullContent;
        
        public TagParsingException(String message, String fullContent) {
            super(message);
            this.fullContent = fullContent;
        }

    }
}