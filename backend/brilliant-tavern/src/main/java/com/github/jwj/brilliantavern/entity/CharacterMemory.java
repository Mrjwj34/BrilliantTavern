package com.github.jwj.brilliantavern.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 角色全局记忆实体
 * 为每个用户和角色卡维护的向量化记忆存储
 */
@Entity
@Table(name = "character_memories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"embedding"}) // 避免日志中打印长向量
public class CharacterMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "character_card_id", nullable = false)
    private UUID characterCardId;

    @Column(name = "memory_content", nullable = false, columnDefinition = "TEXT")
    private String memoryContent;

    /**
     * 嵌入向量，限制为1536维以支持HNSW索引
     * 使用PostgreSQL vector类型存储
     */
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}