package com.authbase.api.dto;

import java.util.List;

public record UserPageResponse(
        List<UserResponse> items,
        long total,
        int page,
        int size,
        int totalPages
) {
    public static UserPageResponse of(List<UserResponse> items, long total, int page, int size) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / (double) size);
        return new UserPageResponse(items, total, page, size, totalPages);
    }
}
