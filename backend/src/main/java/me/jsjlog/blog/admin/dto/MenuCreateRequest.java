package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.admin.domain.MenuType;

/**
 * 메뉴 등록 요청.
 * 수정(MenuRequest)과 달리 menuType 을 받는다 — 만들 때만 정할 수 있고 나중에는 못 바꾼다.
 *
 * sortOrder 를 비우면 같은 자리의 마지막 다음 번호를 매긴다.
 * visible 을 비우면 노출로 만든다.
 */
public record MenuCreateRequest(
        Long parentId,
        String menuName,
        String menuDescription,
        MenuType menuType,
        String routePath,
        Long sortOrder,
        Boolean visible
) {
}
