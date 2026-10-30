package me.jsjlog.blog.post.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.post.dto.ReadingPositionRequest;
import me.jsjlog.blog.post.dto.ReadingPositionResponse;
import me.jsjlog.blog.post.dto.RecentPostListResponse;
import me.jsjlog.blog.post.dto.ViewedPostResponse;
import me.jsjlog.blog.post.service.MemberReadingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 내가 읽은 글. 경로가 {@code /auth/me} 아래인 것은 로그인한 본인 것만 다루기 때문이다.
 * 로그인 확인은 보안 설정이 하고, 누구의 기록인지는 언제나 로그인 정보에서 꺼낸다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/me")
public class MemberReadingController {

	private final MemberReadingService memberReadingService;

	@GetMapping("/recent-posts")
	public ApiResponse<RecentPostListResponse> getRecentPosts(
			@AuthenticationPrincipal MemberPrincipal principal,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size
	) {
		return ApiResponse.ok(memberReadingService.getRecentPosts(principal.getId(), page, size));
	}

	@GetMapping("/viewed-posts")
	public ApiResponse<List<ViewedPostResponse>> getViewedPosts(
			@AuthenticationPrincipal MemberPrincipal principal,
			@RequestParam(required = false) List<Long> postIds
	) {
		return ApiResponse.ok(memberReadingService.getViewedPosts(principal.getId(), postIds));
	}

	@GetMapping("/reading-positions/{postId}")
	public ApiResponse<ReadingPositionResponse> getReadingPosition(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long postId
	) {
		return ApiResponse.ok(memberReadingService.getReadingPosition(principal.getId(), postId));
	}

	@PutMapping("/reading-positions/{postId}")
	public ApiResponse<Void> saveReadingPosition(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long postId,
			@Valid @RequestBody ReadingPositionRequest request
	) {
		memberReadingService.saveReadingPosition(principal.getId(), postId, request);
		return ApiResponse.ok();
	}
}
