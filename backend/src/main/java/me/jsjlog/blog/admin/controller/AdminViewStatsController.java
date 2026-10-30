package me.jsjlog.blog.admin.controller;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminPostViewStatsListResponse;
import me.jsjlog.blog.admin.dto.AdminViewStatsResponse;
import me.jsjlog.blog.admin.service.AdminViewStatsService;
import me.jsjlog.blog.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 요약과 글 목록을 나눈다. 화면이 둘을 따로 받아 먼저 온 쪽부터 그린다 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/blog/stats/views")
public class AdminViewStatsController {

	private final AdminViewStatsService adminViewStatsService;

	@GetMapping
	public ApiResponse<AdminViewStatsResponse> getSummary(@RequestParam(required = false) Integer days) {
		return ApiResponse.ok(adminViewStatsService.getSummary(days));
	}

	@GetMapping("/posts")
	public ApiResponse<AdminPostViewStatsListResponse> getPosts(
			@RequestParam(required = false) Integer days,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size
	) {
		return ApiResponse.ok(adminViewStatsService.getPosts(days, page, size));
	}
}
