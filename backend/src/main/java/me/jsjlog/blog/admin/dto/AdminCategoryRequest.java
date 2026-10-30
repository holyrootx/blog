package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

public record AdminCategoryRequest(
		// 화면이 불러왔던 시점의 값. 지금 서버 값과 다르면 그 사이 누가 고친 것이다.
		// 등록에는 쓰지 않는다
		LocalDateTime updatedAt,
		String name,
		Long sortOrder
) {
}
