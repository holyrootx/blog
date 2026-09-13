package me.jsjlog.blog.admin.dto;

/**
 * 카테고리 등록·수정 요청. 두 경우의 입력이 같아서 하나로 쓴다.
 */
public record AdminCategoryRequest(
        String name,
        Long sortOrder
) {
}
