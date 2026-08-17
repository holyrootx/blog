package me.jsjlog.blog.common.response;

import java.time.OffsetDateTime;
import java.util.List;

import me.jsjlog.blog.common.exception.ErrorCode;

/**
 * API 실패 응답의 공통 포맷입니다.
 *
 * <p>컨트롤러에서 직접 만들기보다 {@code GlobalExceptionHandler}가 예외를
 * 잡아서 이 타입으로 변환합니다. 따라서 일반 컨트롤러 코드는 에러 응답을
 * 직접 신경 쓰지 않고, 필요한 경우 {@code BlogException}만 던지면 됩니다.</p>
 *
 * <p>프론트엔드는 {@code code}를 기준으로 로그인 이동, 404 화면,
 * validation 메시지 출력 같은 처리를 할 수 있습니다. {@code message}는
 * 화면에 보여줄 수 있는 설명이고, API 계약의 핵심은 {@code code}입니다.</p>
 *
 * @param success 실패 응답에서는 항상 {@code false}
 * @param code 에러 코드. 예: {@code PROFILE_NOT_FOUND}
 * @param message 에러 설명 메시지
 * @param errors validation 실패 시 필드별 에러 목록
 * @param path 에러가 발생한 요청 경로
 * @param timestamp 응답 생성 시각
 */
public record ErrorResponse(
	boolean success,
	String code,
	String message,
	List<FieldErrorResponse> errors,
	String path,
	OffsetDateTime timestamp
) {

	/**
	 * 기본 에러 메시지를 그대로 사용하는 실패 응답입니다.
	 */
	public static ErrorResponse of(ErrorCode errorCode, String path) {
		return of(errorCode, errorCode.getMessage(), List.of(), path);
	}

	/**
	 * 에러 코드는 유지하되 메시지만 상황에 맞게 바꿔야 할 때 사용합니다.
	 */
	public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
		return of(errorCode, message, List.of(), path);
	}

	/**
	 * validation 에러처럼 필드별 오류 목록이 필요한 실패 응답입니다.
	 */
	public static ErrorResponse of(
		ErrorCode errorCode,
		String message,
		List<FieldErrorResponse> errors,
		String path
	) {
		return new ErrorResponse(
			false,
			errorCode.getCode(),
			message,
			errors,
			path,
			OffsetDateTime.now()
		);
	}
}
