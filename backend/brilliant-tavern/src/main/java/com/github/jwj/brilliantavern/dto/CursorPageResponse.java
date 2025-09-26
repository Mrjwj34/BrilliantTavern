package com.github.jwj.brilliantavern.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 通用游标分页响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CursorPageResponse<T> {

    @Builder.Default
    private List<T> items = Collections.emptyList();

    private String nextCursor;

    @Builder.Default
    private boolean hasNext = false;
}
