package com.github.jwj.brilliantavern.dto.voice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 聊天会话概要信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionSummaryDTO {
    
    /**
     * 会话ID
     */
    private UUID sessionId;
    
    /**
     * 角色卡ID
     */
    private UUID cardId;
    
    /**
     * 角色卡名称
     */
    private String cardName;
    
    /**
     * 会话开始时间
     */
    private OffsetDateTime startTime;
    
    /**
     * 最后一条消息时间
     */
    private OffsetDateTime lastTime;
    
    /**
     * 消息总数
     */
    private Long messageCount;
    
    /**
     * 第一条消息内容（用于预览）
     */
    private String firstMessage;
    
    /**
     * 会话标题（可以是第一条消息的摘要或自定义标题）
     */
    private String title;
    
    /**
     * 游标字符串，用于分页（基于lastTime）
     */
    private String cursor;
}