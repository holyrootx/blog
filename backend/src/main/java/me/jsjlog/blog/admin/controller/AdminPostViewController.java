package me.jsjlog.blog.admin.controller;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminPostViewListResponse;
import me.jsjlog.blog.admin.dto.AdminPostViewSearchCondition;
import me.jsjlog.blog.admin.service.AdminPostViewService;
import me.jsjlog.blog.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/blog/posts")
public class AdminPostViewController {

	private final AdminPostViewService adminPostViewService;

	@GetMapping("/{postId}/views")
	public ApiResponse<AdminPostViewListResponse> getPostViews(
			@PathVariable Long postId,
			AdminPostViewSearchCondition condition
	) {
		return ApiResponse.ok(adminPostViewService.getPostViews(postId, condition));
	}
}
