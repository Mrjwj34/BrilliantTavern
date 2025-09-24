package com.github.jwj.brilliantavern.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 角色卡评论实体类
 */
@Entity
@Table(name = "card_comments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 被评论的角色卡ID
     */
    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    /**
     * 评论发表者ID
     */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /**
     * 评论内容
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    private java.time.OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private java.time.OffsetDateTime updatedAt;

    // 关联实体
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", insertable = false, updatable = false)
    private CharacterCard characterCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", insertable = false, updatable = false)
    private User author;
}
