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
 * TTS音色实体类
 */
@Entity
@Table(name = "tts_voices", indexes = {
    @Index(name = "idx_tts_voice_reference_id", columnList = "reference_id"),
    @Index(name = "idx_tts_voice_creator", columnList = "creator_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TTSVoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 音色唯一标识符（对应TTS服务的reference_id）
     */
    @Column(name = "reference_id", nullable = false, unique = true)
    private String referenceId;

    /**
     * 音色名称
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 音色描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 创建者ID
     */
    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    /**
     * 是否公开可用
     */
    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = false;

    /**
     * 参考文本
     */
    @Column(name = "reference_text", columnDefinition = "TEXT")
    private String referenceText;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * 是否已删除（软删除）
     */
    @Column(name = "deleted")
    @Builder.Default
    private Boolean deleted = false;

    // 关联实体
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", insertable = false, updatable = false)
    @JsonIgnore  // 避免懒加载序列化问题
    private User creator;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
