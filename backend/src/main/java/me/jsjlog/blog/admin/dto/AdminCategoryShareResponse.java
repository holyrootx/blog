package me.jsjlog.blog.admin.dto;

/** 카테고리 비중. 화면이 "발행 글 기준"이라고 적고 있으므로 발행글만 센다 */
public record AdminCategoryShareResponse(
        Long categoryId,
        String name,
        Long postCount
) {
}
