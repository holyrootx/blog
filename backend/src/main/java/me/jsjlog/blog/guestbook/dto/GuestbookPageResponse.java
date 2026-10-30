package me.jsjlog.blog.guestbook.dto;

import java.util.List;

/**
 * 방명록 한 쪽.
 *
 * <p>쪽 번호로 넘긴다. 글 댓글처럼 이어 붙이면 오래된 인사를 찾아가기 어렵고, 방명록은
 * "몇 쪽쯤에 썼다" 로 기억하는 경우가 많다.</p>
 */
public record GuestbookPageResponse(
		List<GuestbookEntryResponse> items,
		int page,
		int size,
		long totalElements,
		int totalPages,
		/** 화면이 글자 수 안내에 쓴다. 서버 제한과 어긋나지 않게 여기서 준다 */
		int maxLength
) {
}
