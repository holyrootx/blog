package me.jsjlog.blog.post.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.post.dto.PostActiveTimeRequest;
import me.jsjlog.blog.post.service.PostViewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/blog/posts/{postId}/active-time")
public class PostActiveTimeController {

	private final PostViewService postViewService;

	@PostMapping
	public ApiResponse<Measurement> start(@PathVariable Long postId, HttpServletRequest request,
			@AuthenticationPrincipal MemberPrincipal principal) {
		String token = isAdmin(principal) ? null : postViewService.startActiveTime(
				postId, request.getSession(false), memberId(principal));
		return ApiResponse.ok(token == null ? null : new Measurement(token));
	}

	@PutMapping
	public ApiResponse<Void> record(@PathVariable Long postId, @Valid @RequestBody PostActiveTimeRequest body,
			HttpServletRequest request, @AuthenticationPrincipal MemberPrincipal principal) {
		if (!isAdmin(principal)) {
			postViewService.recordActiveTime(postId, request.getSession(false), memberId(principal),
					body.token(), body.activeTimeMillis());
		}
		return ApiResponse.ok(null);
	}

	private static Long memberId(MemberPrincipal principal) {
		return principal == null ? null : principal.getId();
	}

	private static boolean isAdmin(MemberPrincipal principal) {
		return principal != null && principal.getRole() == MemberRole.ADMIN;
	}

	public record Measurement(String token) {
	}
}
