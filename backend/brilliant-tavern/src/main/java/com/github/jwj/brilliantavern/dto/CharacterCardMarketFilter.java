package com.github.jwj.brilliantavern.dto;

import com.github.jwj.brilliantavern.exception.BusinessException;

import java.util.Arrays;

/**
 * 角色市场筛选条件
 */
public enum CharacterCardMarketFilter {
    PUBLIC,
    POPULAR,
    LATEST,
    MY,
    LIKED;

    public static CharacterCardMarketFilter fromString(String value) {
        if (value == null || value.isBlank()) {
            return PUBLIC;
        }
        return Arrays.stream(values())
                .filter(filter -> filter.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "无效的筛选条件"));
    }
}
