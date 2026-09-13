package me.jsjlog.blog.admin.dto;

/**
 * 메뉴 수정 요청.
 * menuType 은 받지 않는다 — 그룹과 항목을 서로 바꾸면 트리가 깨진다.
 */
public record MenuRequest(
        Long parentId,
        String menuName,
        String menuDescription,
        String routePath,
        Long sortOrder,
        Boolean visible
) {
}
