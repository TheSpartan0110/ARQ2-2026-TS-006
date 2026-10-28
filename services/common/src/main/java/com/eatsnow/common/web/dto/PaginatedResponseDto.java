package com.eatsnow.common.web.dto;

import com.eatsnow.common.domain.model.PageResult;

import java.util.List;
import java.util.function.Function;

/**
 * Standard paginated response envelope shared by all EatsNow list endpoints (Spec 3.1).
 */
public record PaginatedResponseDto<T>(
        List<T> items,
        String nextCursor
) {
    public static <E, D> PaginatedResponseDto<D> fromDomain(PageResult<E> pageResult, Function<E, D> mapper) {
        List<D> mappedItems = pageResult.items().stream().map(mapper).toList();
        return new PaginatedResponseDto<>(mappedItems, pageResult.nextCursor());
    }
}
