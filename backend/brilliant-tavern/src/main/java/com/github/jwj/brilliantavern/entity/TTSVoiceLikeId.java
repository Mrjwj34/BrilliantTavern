package com.github.jwj.brilliantavern.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * TTS语音点赞复合主键
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class TTSVoiceLikeId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "voice_id", nullable = false)
    private Long voiceId;
}
