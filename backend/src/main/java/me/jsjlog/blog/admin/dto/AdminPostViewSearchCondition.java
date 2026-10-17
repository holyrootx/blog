package me.jsjlog.blog.admin.dto;

/**
 * 글 하나의 조회 기록 조회 조건.
 *
 * @param viewer 비우면 전체
 */
public record AdminPostViewSearchCondition(
        Integer page,
        Integer size,
        AdminPostViewerType viewer
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public int pageOrDefault() {
        return page == null || page < 0 ? DEFAULT_PAGE : page;
    }

    public int sizeOrDefault() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }

        return Math.min(size, MAX_SIZE);
    }
}
