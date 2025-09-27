package com.github.jwj.brilliantavern.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 评论点赞实体类
 */
@Entity
@Table(name = "comment_likes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(CommentLike.CommentLikeId.class)
public class CommentLike {

    /**
     * 用户ID
     */
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 评论ID
     */
    @Id
    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    /**
     * 点赞时间
     */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    // 关联实体
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", insertable = false, updatable = false)
    @JsonIgnore
    private CardComment comment;

    /**
     * 复合主键类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentLikeId implements java.io.Serializable {
        private UUID userId;
        private Long commentId;
    }
}