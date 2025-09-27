package com.github.jwj.brilliantavern.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.jwj.brilliantavern.entity.converter.ChatHistoryRoleConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 对话历史实体类
 * 用于实现多轮对话记忆
 */
@Entity
@Table(name = "chat_history", indexes = {
    @Index(name = "idx_chat_history_session_id", columnList = "session_id"),
    @Index(name = "idx_chat_history_user_card", columnList = "user_id,card_id"),
    @Index(name = "idx_chat_history_history_id", columnList = "history_id"),
    @Index(name = "idx_chat_history_user_history", columnList = "user_id,history_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 历史记录ID - 标识一个完整的历史记录，用户在历史列表中看到的每一项
     */
    @Column(name = "history_id", nullable = false)
    private UUID historyId;

    /**
     * 会话ID - 标识一次技术会话连接，用于WebSocket通信管理
     */
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /**
     * 参与对话的用户ID
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 对话的角色卡ID
     */
    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    /**
     * 角色 ('user' 或 'assistant')
     */
    @Column(name = "role", nullable = false, length = 20)
    @Convert(converter = ChatHistoryRoleConverter.class)
    private Role role;

    /**
     * 对话的文本内容
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 消息时间戳
     */
    @Column(name = "timestamp", nullable = false)
    private OffsetDateTime timestamp;

    /**
     * 消息角色枚举
     */
    public enum Role {
        USER, ASSISTANT
    }

    // 关联实体
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", insertable = false, updatable = false)
    @JsonIgnore
    private CharacterCard characterCard;
}
