package com.eatsnow.common.domain.model;

import java.util.List;

/**
 * Common paginated result container for list queries (Spec 3.1).
 */
public record PageResult<T>(
        List<T> items,
        String nextCursor
) {
    public PageResult {
        if (items == null) {
            items = List.of();
        }
    }
}
