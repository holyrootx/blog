package com.jsj.blog.common;

import java.time.Instant;

import com.jsj.blog.common.response.ApiResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버 상태 확인용 컨트롤러입니다.
 *
 * <p>비즈니스 API라기보다 배포, Nginx 프록시, 서버 기동 여부를 빠르게 확인하기 위한
 * 최소 API입니다. 이후 운영 모니터링이나 GitHub Actions 배포 검증에서도 사용할 수 있습니다.</p>
 */
@RestController
public class HealthController {

	/**
	 * 백엔드 애플리케이션이 정상 기동 중인지 확인합니다.
	 */
	@GetMapping("/api/health")
	public ApiResponse<HealthResponse> health() {
		return ApiResponse.ok(new HealthResponse("ok", "blog-backend", Instant.now()));
	}

	/**
	 * health check 응답 본문입니다.
	 *
	 * <p>외부 API 응답 타입이지만 현재 health API에서만 쓰이므로 컨트롤러 내부에 둡니다.
	 * 다른 API에서 재사용해야 할 시점이 오면 별도 DTO 파일로 분리합니다.</p>
	 */
	private record HealthResponse(
		String status,
		String service,
		Instant checkedAt
	) {
	}
}
