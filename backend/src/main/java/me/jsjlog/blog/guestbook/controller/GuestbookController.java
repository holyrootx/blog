package me.jsjlog.blog.guestbook.controller;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.guestbook.dto.GuestbookPageResponse;
import me.jsjlog.blog.guestbook.dto.GuestbookWriteRequest;
import me.jsjlog.blog.guestbook.service.GuestbookService;
import me.jsjlog.blog.post.dto.CommentReportRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/blog/guestbook")
public class GuestbookController {

	private final GuestbookService guestbookService;

	/**
	 * 누구나 읽는다. 로그인했으면 내 글 표시와 비밀글 열람이 더해진다.
	 * {@code entry} 를 주면 그 글이 있는 쪽을 준다(알림에서 찾아올 때).
	 */
	@GetMapping
	public ApiResponse<GuestbookPageResponse> getEntries(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			@RequestParam(required = false) Long entry,
			@AuthenticationPrincipal MemberPrincipal principal
	) {
		return ApiResponse.ok(guestbookService.getEntries(
				page,
				size,
				entry,
				principalId(principal),
				principal != null && principal.isAdmin()
		));
	}

	@PostMapping
	public ApiResponse<Long> write(
			@RequestBody GuestbookWriteRequest request,
			@AuthenticationPrincipal MemberPrincipal principal
	) {
		return ApiResponse.ok(guestbookService.write(request, principalId(principal)));
	}

	/** 내 글 고치기. 남의 글은 운영자라도 고칠 수 없다 — 운영자에게는 가리기가 있다 */
	@PutMapping("/{entryId}")
	public ApiResponse<Void> update(
			@PathVariable Long entryId,
			@RequestBody GuestbookWriteRequest request,
			@AuthenticationPrincipal MemberPrincipal principal
	) {
		guestbookService.update(entryId, request, principalId(principal));
		return ApiResponse.ok();
	}

	@DeleteMapping("/{entryId}")
	public ApiResponse<Void> delete(
			@PathVariable Long entryId,
			@AuthenticationPrincipal MemberPrincipal principal
	) {
		guestbookService.delete(entryId, principalId(principal));
		return ApiResponse.ok();
	}

	@PostMapping("/{entryId}/reports")
	public ApiResponse<Void> report(
			@PathVariable Long entryId,
			@RequestBody CommentReportRequest request,
			@AuthenticationPrincipal MemberPrincipal principal
	) {
		guestbookService.report(
				entryId,
				request == null ? null : request.reason(),
				request == null ? null : request.detail(),
				principalId(principal)
		);
		return ApiResponse.ok();
	}

	private Long principalId(MemberPrincipal principal) {
		return principal == null ? null : principal.getId();
	}
}
