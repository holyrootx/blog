package me.jsjlog.blog.common;

import java.time.Instant;

import me.jsjlog.blog.common.response.ApiResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	@GetMapping("/api/health")
	public ApiResponse<HealthResponse> health() {
		return ApiResponse.ok(new HealthResponse("ok", "blog-backend", Instant.now()));
	}

	private record HealthResponse(
		String status,
		String service,
		Instant checkedAt
	) {
	}
}
