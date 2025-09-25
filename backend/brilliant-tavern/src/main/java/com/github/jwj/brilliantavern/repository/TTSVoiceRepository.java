package com.github.jwj.brilliantavern.repository;

import com.github.jwj.brilliantavern.entity.TTSVoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TTS音色Repository接口
 */
@Repository
public interface TTSVoiceRepository extends JpaRepository<TTSVoice, Long> {

    /**
     * 根据reference_id查找音色（未删除）
     */
    Optional<TTSVoice> findByReferenceIdAndDeletedFalse(String referenceId);

    /**
     * 根据创建者查找音色
     */
    Page<TTSVoice> findByCreatorIdAndDeletedFalse(UUID creatorId, Pageable pageable);

    /**
     * 查找公开可用的音色
     */
    Page<TTSVoice> findByIsPublicTrueAndDeletedFalse(Pageable pageable);

    /**
     * 根据名称模糊查询音色
     */
    @Query("SELECT v FROM TTSVoice v WHERE v.name LIKE %:name% AND v.deleted = false AND (v.isPublic = true OR v.creatorId = :userId)")
    Page<TTSVoice> searchByNameAndAccessible(@Param("name") String name, @Param("userId") UUID userId, Pageable pageable);

    /**
     * 查找用户可访问的音色（自己创建的+公开的）
     */
    @Query("SELECT v FROM TTSVoice v WHERE v.deleted = false AND (v.creatorId = :userId OR v.isPublic = true)")
    Page<TTSVoice> findAccessibleVoices(@Param("userId") UUID userId, Pageable pageable);

    /**
     * 查找用户可访问的音色列表（非分页）
     */
    @Query("SELECT v FROM TTSVoice v WHERE v.deleted = false AND (v.creatorId = :userId OR v.isPublic = true) ORDER BY v.createdAt DESC")
    List<TTSVoice> findAccessibleVoices(@Param("userId") UUID userId);

    /**
     * 查找公开音色列表（非分页）
     */
    @Query("SELECT v FROM TTSVoice v WHERE v.deleted = false AND v.isPublic = true ORDER BY v.createdAt DESC")
    List<TTSVoice> findPublicVoices();

    /**
     * 根据创建者查找音色列表（非分页）
     */
    @Query("SELECT v FROM TTSVoice v WHERE v.deleted = false AND v.creatorId = :creatorId ORDER BY v.createdAt DESC")
    List<TTSVoice> findByCreatorIdAndNotDeleted(@Param("creatorId") UUID creatorId);

    /**
     * 根据ID查找未删除的音色
     */
    @Query("SELECT v FROM TTSVoice v WHERE v.id = :id AND v.deleted = false")
    Optional<TTSVoice> findByIdAndNotDeleted(@Param("id") Long id);

    /**
     * 搜索用户可访问的音色
     */
    @Query("""
        SELECT v FROM TTSVoice v 
        WHERE v.deleted = false 
        AND (v.creatorId = :userId OR v.isPublic = true)
        AND (LOWER(v.name) LIKE LOWER(CONCAT('%', :keyword, '%')) 
             OR LOWER(v.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY v.createdAt DESC
        """)
    List<TTSVoice> searchAccessibleVoices(@Param("keyword") String keyword, @Param("userId") UUID userId);

    /**
     * 搜索用户创建的音色
     */
    @Query("""
        SELECT v FROM TTSVoice v 
        WHERE v.deleted = false 
        AND v.creatorId = :userId
        AND (LOWER(v.name) LIKE LOWER(CONCAT('%', :keyword, '%')) 
             OR LOWER(v.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY v.createdAt DESC
        """)
    List<TTSVoice> searchUserVoices(@Param("keyword") String keyword, @Param("userId") UUID userId);

    /**
     * 软删除音色
     */
    @Modifying
    @Query("UPDATE TTSVoice v SET v.deleted = true WHERE v.id = :id")
    void softDeleteById(@Param("id") Long id);

    /**
     * 统计用户创建的音色数量
     */
    long countByCreatorIdAndDeletedFalse(UUID creatorId);
}
