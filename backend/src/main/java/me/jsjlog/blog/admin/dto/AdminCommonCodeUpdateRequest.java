package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

/**
 * 공통 코드 수정. 코드 값은 받지 않는다 — 지난 데이터와 이력이 그 값을 가리킨다.
 *
 * @param sortOrder 비우면 그대로 둔다
 * @param enabled   비우면 그대로 둔다. 서버 코드(enum)에 있는 코드는 끌 수 없다
 */
public record AdminCommonCodeUpdateRequest(
        // 화면이 불러왔던 시점의 값. 지금 서버 값과 다르면 그 사이 누가 고친 것이다
        LocalDateTime updatedAt,
        String codeName,
        String description,
        Integer sortOrder,
        Boolean enabled
) {
}
