package com.github.jwj.brilliantavern.repository;

import com.github.jwj.brilliantavern.entity.CharacterMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 角色记忆存储库
 * 支持向量相似度搜索和基础CRUD操作
 */
@Repository
public interface CharacterMemoryRepository extends JpaRepository<CharacterMemory, Long> {

    /**
     * 根据用户ID和角色卡ID查找记忆
     */
    List<CharacterMemory> findByUserIdAndCharacterCardIdOrderByCreatedAtDesc(UUID userId, UUID characterCardId);

    /**
     * 根据用户ID和角色卡ID查找记忆（分页）
     */
    List<CharacterMemory> findByUserIdAndCharacterCardIdOrderByCreatedAtDesc(UUID userId, UUID characterCardId, Pageable pageable);

    /**
     * 根据用户ID查找所有记忆
     */
    List<CharacterMemory> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * 向量相似度搜索 - 查找与给定向量最相似的记忆
     * 使用余弦相似度进行搜索
     * 
     * @param userId 用户ID
     * @param characterCardId 角色卡ID
     * @param queryEmbedding 查询向量 (1536维度)
     * @param similarityThreshold 相似度阈值 (0-1之间，越接近1越相似)
     * @param limit 返回结果数量限制
     * @return 相似记忆列表，按相似度降序排列
     */
    @Query(value = """
        SELECT m.*, 
               (1 - (m.embedding <=> CAST(:queryEmbedding AS vector))) AS similarity_score
        FROM character_memories m 
        WHERE m.user_id = :userId 
          AND m.character_card_id = :characterCardId
          AND (1 - (m.embedding <=> CAST(:queryEmbedding AS vector))) >= :similarityThreshold
        ORDER BY m.embedding <=> CAST(:queryEmbedding AS vector) ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findSimilarMemories(@Param("userId") UUID userId,
                                     @Param("characterCardId") UUID characterCardId,
                                     @Param("queryEmbedding") String queryEmbedding,
                                     @Param("similarityThreshold") double similarityThreshold,
                                     @Param("limit") int limit);

    /**
     * 向量相似度搜索（仅用户范围）
     * 
     * @param userId 用户ID
     * @param queryEmbedding 查询向量
     * @param similarityThreshold 相似度阈值
     * @param limit 返回结果数量限制
     * @return 相似记忆列表
     */
    @Query(value = """
        SELECT m.*, 
               (1 - (m.embedding <=> CAST(:queryEmbedding AS vector))) AS similarity_score
        FROM character_memories m 
        WHERE m.user_id = :userId 
          AND (1 - (m.embedding <=> CAST(:queryEmbedding AS vector))) >= :similarityThreshold
        ORDER BY m.embedding <=> CAST(:queryEmbedding AS vector) ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findSimilarMemoriesByUser(@Param("userId") UUID userId,
                                           @Param("queryEmbedding") String queryEmbedding,
                                           @Param("similarityThreshold") double similarityThreshold,
                                           @Param("limit") int limit);

    /**
     * 删除用户和角色卡的所有记忆
     */
    void deleteByUserIdAndCharacterCardId(UUID userId, UUID characterCardId);

    /**
     * 删除用户的所有记忆
     */
    void deleteByUserId(UUID userId);

    /**
     * 统计用户和角色卡的记忆数量
     */
    long countByUserIdAndCharacterCardId(UUID userId, UUID characterCardId);

    /**
     * 统计用户的总记忆数量
     */
    long countByUserId(UUID userId);
}