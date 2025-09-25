package com.github.jwj.brilliantavern.repository;

import com.github.jwj.brilliantavern.entity.TTSVoiceLike;
import com.github.jwj.brilliantavern.entity.TTSVoiceLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * TTS语音点赞仓储
 */
@Repository
public interface TTSVoiceLikeRepository extends JpaRepository<TTSVoiceLike, TTSVoiceLikeId> {

    boolean existsByIdUserIdAndIdVoiceId(UUID userId, Long voiceId);

    long countByIdVoiceId(Long voiceId);

    @Query("select l.id.voiceId from TTSVoiceLike l where l.id.userId = :userId and l.id.voiceId in :voiceIds")
    List<Long> findLikedVoiceIds(@Param("userId") UUID userId, @Param("voiceIds") Collection<Long> voiceIds);
}
