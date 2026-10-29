package me.jsjlog.blog.admin.controller;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminCommentReplyRequest;
import me.jsjlog.blog.admin.dto.AdminGuestbookDetail;
import me.jsjlog.blog.admin.dto.AdminGuestbookListResponse;
import me.jsjlog.blog.admin.dto.AdminGuestbookSearchCondition;
import me.jsjlog.blog.admin.dto.AdminGuestbookVisibilityRequest;
import me.jsjlog.blog.admin.service.AdminGuestbookService;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.MemberPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/blog/guestbook")
public class AdminGuestbookController {

	private final AdminGuestbookService adminGuestbookService;

	@GetMapping
	public ApiResponse<AdminGuestbookListResponse> getEntries(AdminGuestbookSearchCondition condition) {
		return ApiResponse.ok(adminGuestbookService.getEntries(condition));
	}

	/** 글 하나와 신고 목록 */
	@GetMapping("/{entryId}")
	public ApiResponse<AdminGuestbookDetail> getDetail(@PathVariable Long entryId) {
		return ApiResponse.ok(adminGuestbookService.getDetail(entryId));
	}

	/** 운영자 답글. 글마다 하나라 달기와 고치기가 같은 자리다 */
	@PutMapping("/{entryId}/reply")
	public ApiResponse<Void> reply(
			@PathVariable Long entryId,
			@RequestBody AdminCommentReplyRequest request,
			@AuthenticationPrincipal MemberPrincipal principal
	) {
		adminGuestbookService.reply(
				entryId,
				request == null ? null : request.content(),
				principalId(principal)
		);
		return ApiResponse.ok();
	}

	@DeleteMapping("/{entryId}/reply")
	public ApiResponse<Void> removeReply(
			@PathVariable Long entryId,
			@AuthenticationPrincipal MemberPrincipal principal
	) {
		adminGuestbookService.removeReply(entryId, principalId(principal));
		return ApiResponse.ok();
	}

	@PutMapping("/{entryId}/visibility")
	public ApiResponse<Void> updateVisibility(
			@PathVariable Long entryId,
			@RequestBody AdminGuestbookVisibilityRequest request,
			@AuthenticationPrincipal MemberPrincipal principal
	) {
		adminGuestbookService.updateVisibility(
				entryId,
				request == null ? null : request.hidden(),
				principalId(principal)
		);
		return ApiResponse.ok();
	}

	/** 신고를 봤지만 글은 그대로 둔다 */
	@PostMapping("/{entryId}/reports/dismiss")
	public ApiResponse<Void> dismissReports(
			@PathVariable Long entryId,
			@AuthenticationPrincipal MemberPrincipal principal
	) {
		adminGuestbookService.dismissReports(entryId, principalId(principal));
		return ApiResponse.ok();
	}

	private Long principalId(MemberPrincipal principal) {
		return principal == null ? null : principal.getId();
	}
}
