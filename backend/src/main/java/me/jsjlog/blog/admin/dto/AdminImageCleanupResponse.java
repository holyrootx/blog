package me.jsjlog.blog.admin.dto;

/** 관리자가 고른 이미지 정리 결과. 선택 건수와 실제 삭제 건수를 구분해서 전달한다. */
public record AdminImageCleanupResponse(
        int requestedCount,
        int deletedCount,
        int skippedUsedCount,
        int notFoundCount,
        int failedCount
) {

    public static AdminImageCleanupResponse empty() {
        return new AdminImageCleanupResponse(0, 0, 0, 0, 0);
    }
}
