package com.github.jwj.brilliantavern.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 用户点赞关联实体类
 */
@Entity
@Table(name = "user_likes")
@IdClass(UserLike.UserLikeId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLike {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "card_id")
    private UUID cardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", insertable = false, updatable = false)
    @JsonIgnore
    private CharacterCard characterCard;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * 复合主键类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserLikeId implements Serializable {
        private UUID userId;
        private UUID cardId;
    }
}
