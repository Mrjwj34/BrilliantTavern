package com.github.jwj.brilliantavern.dto.voice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 语音聊天会话请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceChatSessionRequest {
    
    /**
     * 角色卡ID
     */
    private UUID characterCardId;
    
    /**
     * 会话ID（可选，用于继续之前的会话）
     */
    private UUID sessionId;
    
    /**
     * 是否需要加载历史对话
     */
    @Builder.Default
    private Boolean loadHistory = true;
    
    /**
     * 加载历史对话的条数限制
     */
    @Builder.Default
    private Integer historyLimit = 10;
}
