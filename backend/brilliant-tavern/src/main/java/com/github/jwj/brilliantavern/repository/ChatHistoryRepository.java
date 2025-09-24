package com.github.jwj.brilliantavern.repository;

import com.github.jwj.brilliantavern.entity.ChatHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 对话历史Repository接口
 */
@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    /**
     * 根据用户ID和角色卡ID查找最近的对话历史
     */
    @Query("SELECT ch FROM ChatHistory ch WHERE ch.userId = :userId AND ch.cardId = :cardId ORDER BY ch.timestamp DESC")
    List<ChatHistory> findRecentChatHistory(@Param("userId") UUID userId, 
                                          @Param("cardId") UUID cardId, 
                                          Pageable pageable);

    /**
     * 根据会话ID查找对话历史
     */
    List<ChatHistory> findBySessionIdOrderByTimestampAsc(UUID sessionId);

    /**
     * 根据用户ID和角色卡ID查找所有会话ID
     */
    @Query("SELECT DISTINCT ch.sessionId FROM ChatHistory ch WHERE ch.userId = :userId AND ch.cardId = :cardId ORDER BY MIN(ch.timestamp) DESC")
    List<UUID> findSessionIdsByUserAndCard(@Param("userId") UUID userId, @Param("cardId") UUID cardId);

    /**
     * 根据会话ID删除对话历史
     */
    void deleteBySessionId(UUID sessionId);

    /**
     * 统计用户与角色卡的对话总数
     */
    @Query("SELECT COUNT(ch) FROM ChatHistory ch WHERE ch.userId = :userId AND ch.cardId = :cardId")
    long countByUserAndCard(@Param("userId") UUID userId, @Param("cardId") UUID cardId);
}
