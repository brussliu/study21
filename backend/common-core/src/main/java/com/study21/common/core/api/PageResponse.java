package com.study21.common.core.api;

import java.util.List;

/**
 * 未来分页格式预留。本阶段无数据库、无分页实现。
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
