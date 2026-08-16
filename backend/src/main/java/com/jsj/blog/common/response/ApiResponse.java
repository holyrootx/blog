package com.jsj.blog.common.response;

import java.time.OffsetDateTime;

/**
 * API 성공 응답의 공통 포맷입니다.
 *
 * <p>컨트롤러는 성공 시 이 타입으로 응답을 감싸서 내려줍니다.
 * 프론트엔드는 모든 성공 응답에서 {@code success}, {@code code},
 * {@code message}, {@code data}, {@code timestamp} 필드를 같은 방식으로
 * 읽을 수 있습니다.</p>
 *
 * <p>에러 응답은 {@link ErrorResponse}가 담당합니다. 성공/실패 응답을
 * 한 클래스로 합치지 않은 이유는 validation 에러의 {@code errors},
 * 요청 경로의 {@code path}처럼 실패 응답에만 필요한 값이 있기 때문입니다.</p>
 *
 * @param success 성공 여부. 성공 응답에서는 항상 {@code true}
 * @param code 프론트엔드가 분기 처리할 응답 코드
 * @param message 사용자 또는 개발자가 읽을 수 있는 기본 메시지
 * @param data 실제 응답 데이터. 데이터가 없으면 {@code null}
 * @param timestamp 응답 생성 시각
 * @param <T> 응답 데이터 타입
 */
public record ApiResponse<T>(
	boolean success,
	String code,
	String message,
	T data,
	OffsetDateTime timestamp
) {

	/**
	 * 가장 많이 쓰는 성공 응답 생성 메서드입니다.
	 *
	 * <p>예: {@code return ApiResponse.ok(profileService.getProfile());}</p>
	 */
	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, "OK", "요청이 성공했습니다.", data, OffsetDateTime.now());
	}

	/**
	 * 기본 성공 메시지를 상황에 맞게 바꿔야 할 때 사용합니다.
	 *
	 * <p>예: 저장 API에서 {@code "프로필이 저장되었습니다."}처럼
	 * 조금 더 구체적인 메시지를 내려주고 싶을 때 사용합니다.</p>
	 */
	public static <T> ApiResponse<T> ok(String message, T data) {
		return new ApiResponse<>(true, "OK", message, data, OffsetDateTime.now());
	}

	/**
	 * 응답 데이터가 없는 성공 처리에 사용합니다.
	 *
	 * <p>예: 삭제, 로그아웃, 단순 상태 변경처럼 본문 data가 필요 없는 API.</p>
	 */
	public static ApiResponse<Void> ok() {
		return new ApiResponse<>(true, "OK", "요청이 성공했습니다.", null, OffsetDateTime.now());
	}
}
