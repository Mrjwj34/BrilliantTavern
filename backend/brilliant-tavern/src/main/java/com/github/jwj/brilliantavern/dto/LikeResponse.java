package com.github.jwj.brilliantavern.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 点赞操作响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "点赞操作响应")
public class LikeResponse {
    
    @Schema(description = "是否点赞", example = "true")
    private boolean isLiked;
    
    @Schema(description = "点赞数", example = "42")
    private int likesCount;
}
