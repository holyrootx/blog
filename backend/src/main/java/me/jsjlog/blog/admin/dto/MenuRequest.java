package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

/**
 * 메뉴 수정 요청.
 * menuType 은 받지 않는다 — 그룹과 항목을 서로 바꾸면 트리가 깨진다.
 */
public record MenuRequest(
        // 화면이 불러왔던 시점의 값. 지금 서버 값과 다르면 그 사이 누가 고친 것이다
        LocalDateTime updatedAt,
        Long parentId,
        String menuName,
        String menuDescription,
        String routePath,
        Long sortOrder,
        Boolean visible
) {
}
