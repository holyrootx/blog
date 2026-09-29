package me.jsjlog.blog.admin.dto;

import java.util.List;

public record AdminImageListResponse(
        List<AdminImageResponse> items,
        /** 이 페이지에서 아무 데서도 안 쓰는 것들의 합계 */
        long unusedBytes,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
