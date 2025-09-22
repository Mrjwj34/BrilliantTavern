package com.github.jwj.brilliantavern.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JWT认证响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtResponse {

    private String token;
    @Builder.Default
    private String type = "Bearer";
    private UUID userId;
    private String username;
    private String email;
    private OffsetDateTime expiresAt;
}
