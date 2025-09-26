package com.github.jwj.brilliantavern.dto;

import com.github.jwj.brilliantavern.exception.BusinessException;

import java.util.Arrays;

/**
 * 音色市场筛选条件
 */
public enum VoiceMarketFilter {
    PUBLIC,
    POPULAR,
    LATEST,
    MY,
    LIKED;

    public static VoiceMarketFilter fromString(String value) {
        if (value == null || value.isBlank()) {
            return PUBLIC;
        }
        return Arrays.stream(values())
                .filter(filter -> filter.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "无效的音色筛选条件"));
    }

    public boolean requiresLogin() {
        return this == MY || this == LIKED;
    }
}
