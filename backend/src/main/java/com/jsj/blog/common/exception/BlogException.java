package com.jsj.blog.common.exception;

/**
 * 블로그 서비스에서 의도적으로 발생시키는 비즈니스 예외입니다.
 *
 * <p>DB 장애나 NullPointerException 같은 예상 못 한 시스템 오류가 아니라,
 * "프로필이 없다", "slug가 중복됐다", "공개된 글이 아니다"처럼 서비스 규칙상
 * 실패가 명확한 상황에서 사용합니다.</p>
 *
 * <p>컨트롤러나 서비스에서 이 예외를 던지면 {@link GlobalExceptionHandler}가
 * 잡아서 {@code ErrorResponse}로 변환합니다.</p>
 */
public class BlogException extends RuntimeException {

	private final ErrorCode errorCode;

	/**
	 * {@link ErrorCode}에 정의된 기본 메시지를 그대로 사용합니다.
	 */
	public BlogException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	/**
	 * 에러 코드는 유지하되, 상황에 맞는 상세 메시지를 내려주고 싶을 때 사용합니다.
	 */
	public BlogException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	/**
	 * 전역 예외 처리기가 HTTP status와 code를 꺼내기 위해 사용합니다.
	 */
	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
