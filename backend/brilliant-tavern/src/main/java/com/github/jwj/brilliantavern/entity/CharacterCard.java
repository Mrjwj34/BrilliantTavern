package com.github.jwj.brilliantavern.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 角色卡实体类
 */
@Entity
@Table(name = "character_cards")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", insertable = false, updatable = false)
    @JsonIgnore
    private User creator;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "short_description", columnDefinition = "TEXT")
    private String shortDescription;

    @Column(name = "greeting_message", columnDefinition = "TEXT")
    private String greetingMessage;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = false;

    @Column(name = "likes_count", nullable = false)
    @Builder.Default
    private Integer likesCount = 0;

    @Column(name = "tts_voice_id", length = 100)
    private String ttsVoiceId;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "card_data", nullable = false, columnDefinition = "JSONB")
    private CharacterCardData cardData;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * 角色卡数据结构
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CharacterCardData {
        private String description;
        private String personality;
        private String scenario;
        private ExampleDialog[] exampleDialogs;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ExampleDialog {
            private String user;
            private String assistant;
        }
    }
}
