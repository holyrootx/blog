package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

/**
 * 카테고리 등록·수정 요청. 두 경우의 입력이 같아서 하나로 쓴다.
 */
public record AdminCategoryRequest(
        // 화면이 불러왔던 시점의 값. 지금 서버 값과 다르면 그 사이 누가 고친 것이다.
        // 등록에는 쓰지 않는다
        LocalDateTime updatedAt,
        String name,
        Long sortOrder
) {
}
