package com.github.jwj.brilliantavern.repository;

import com.github.jwj.brilliantavern.entity.CardComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 角色卡评论Repository
 */
@Repository
public interface CardCommentRepository extends JpaRepository<CardComment, Long> {
    
    /**
     * 按卡片ID和创建时间排序查找评论（分页）
     */
    @Query("SELECT c FROM CardComment c WHERE c.cardId = :cardId AND c.parentCommentId IS NULL " +
           "ORDER BY " +
           "CASE WHEN c.isPinned = true THEN 0 ELSE 1 END, " +
           "c.pinnedAt DESC, " +
           "CASE WHEN :sortBy = 'likes_count' AND :sortOrder = 'desc' THEN c.likesCount END DESC, " +
           "CASE WHEN :sortBy = 'likes_count' AND :sortOrder = 'asc' THEN c.likesCount END ASC, " +
           "CASE WHEN :sortBy = 'created_at' AND :sortOrder = 'desc' THEN c.createdAt END DESC, " +
           "CASE WHEN :sortBy = 'created_at' AND :sortOrder = 'asc' THEN c.createdAt END ASC")
    Slice<CardComment> findTopLevelCommentsByCardId(@Param("cardId") UUID cardId,
                                                   @Param("sortBy") String sortBy,
                                                   @Param("sortOrder") String sortOrder,
                                                   Pageable pageable);
    
    /**
     * 按卡片ID和游标查找评论（无缝分页）
     */
    @Query("SELECT c FROM CardComment c WHERE c.cardId = :cardId AND c.parentCommentId IS NULL " +
           "AND c.isPinned = false " +
           "AND (:cursor IS NULL OR " +
           "  ((:sortBy = 'likes_count' AND :sortOrder = 'desc' AND (c.likesCount < :cursorLikes OR (c.likesCount = :cursorLikes AND c.id > :cursor))) OR " +
           "   (:sortBy = 'likes_count' AND :sortOrder = 'asc' AND (c.likesCount > :cursorLikes OR (c.likesCount = :cursorLikes AND c.id > :cursor))) OR " +
           "   (:sortBy = 'created_at' AND :sortOrder = 'desc' AND c.createdAt < :cursorCreatedAt) OR " +
           "   (:sortBy = 'created_at' AND :sortOrder = 'asc' AND c.createdAt > :cursorCreatedAt))) " +
           "ORDER BY " +
           "CASE WHEN :sortBy = 'likes_count' AND :sortOrder = 'desc' THEN c.likesCount END DESC, " +
           "CASE WHEN :sortBy = 'likes_count' AND :sortOrder = 'asc' THEN c.likesCount END ASC, " +
           "CASE WHEN :sortBy = 'created_at' AND :sortOrder = 'desc' THEN c.createdAt END DESC, " +
           "CASE WHEN :sortBy = 'created_at' AND :sortOrder = 'asc' THEN c.createdAt END ASC")
    List<CardComment> findTopLevelCommentsByCursor(@Param("cardId") UUID cardId,
                                                  @Param("sortBy") String sortBy,
                                                  @Param("sortOrder") String sortOrder,
                                                  @Param("cursor") Long cursor,
                                                  @Param("cursorLikes") Integer cursorLikes,
                                                  @Param("cursorCreatedAt") java.time.OffsetDateTime cursorCreatedAt,
                                                  Pageable pageable);
    
    /**
     * 查找指定评论的回复
     */
    @Query("SELECT c FROM CardComment c WHERE c.parentCommentId = :parentId ORDER BY c.createdAt ASC")
    List<CardComment> findRepliesByParentId(@Param("parentId") Long parentId);
    
    /**
     * 统计指定评论的回复数量
     */
    @Query("SELECT COUNT(c) FROM CardComment c WHERE c.parentCommentId = :parentId")
    Long countRepliesByParentId(@Param("parentId") Long parentId);
    
    /**
     * 批量统计多个评论的回复数量
     */
    @Query("SELECT c.parentCommentId, COUNT(c) FROM CardComment c WHERE c.parentCommentId IN :parentIds GROUP BY c.parentCommentId")
    List<Object[]> countRepliesByParentIds(@Param("parentIds") List<Long> parentIds);
    
    /**
     * 统计角色卡的评论总数（不包括回复）
     */
    @Query("SELECT COUNT(c) FROM CardComment c WHERE c.cardId = :cardId AND c.parentCommentId IS NULL")
    Long countTopLevelCommentsByCardId(@Param("cardId") UUID cardId);
    
    /**
     * 更新评论的置顶状态
     */
    @Modifying
    @Query("UPDATE CardComment c SET c.isPinned = :isPinned, " +
           "c.pinnedAt = CASE WHEN :isPinned = true THEN CURRENT_TIMESTAMP ELSE NULL END " +
           "WHERE c.id = :commentId")
    int updatePinnedStatus(@Param("commentId") Long commentId, @Param("isPinned") Boolean isPinned);
    
    /**
     * 取消同一角色卡下其他评论的置顶状态
     */
    @Modifying
    @Query("UPDATE CardComment c SET c.isPinned = false, c.pinnedAt = null WHERE c.cardId = :cardId AND c.id != :excludeCommentId")
    int unpinOtherComments(@Param("cardId") UUID cardId, @Param("excludeCommentId") Long excludeCommentId);
    
    /**
     * 查找用户在指定角色卡的评论
     */
    List<CardComment> findByCardIdAndAuthorIdOrderByCreatedAtDesc(UUID cardId, UUID authorId);
}