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
    private Boolean loadHistory = false; // 默认改为false，不加载历史
    
    /**
     * 加载历史对话的条数限制
     */
    @Builder.Default
    private Integer historyLimit = 10;
    
    /**
     * 是否创建新会话（即使存在历史会话也创建新的）
     */
    @Builder.Default
    private Boolean createNew = true; // 默认创建新会话
}
