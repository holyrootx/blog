package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.post.domain.PostStatus;

/**
 * 글 목록 조회조건.
 * page 는 0부터, size 는 최대 100 이다. 둘 다 비면 서비스에서 기본값으로 채운다.
 */
public record AdminPostSearchCondition(
        Integer page,
        Integer size,
        PostStatus status,
        Long categoryId,
        String keyword
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
