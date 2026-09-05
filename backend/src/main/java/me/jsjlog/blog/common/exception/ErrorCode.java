package me.jsjlog.blog.common.exception;

import org.springframework.http.HttpStatus;

/**
 * API에서 사용하는 에러 코드 목록입니다.
 *
 * <p>에러 코드는 DB가 아니라 코드에 enum으로 고정합니다. 에러 코드는
 * 프론트엔드와 백엔드가 공유하는 API 계약이기 때문입니다. 예를 들어
 * 프론트엔드는 {@code UNAUTHORIZED}를 받으면 로그인 화면으로 이동하고,
 * {@code PROFILE_NOT_FOUND}를 받으면 프로필 없음 화면을 보여줄 수 있습니다.</p>
 *
 * <p>새 도메인 에러가 필요하면 이 enum에 값을 추가하고,
 * 서비스 계층에서는 {@code throw new BlogException(ErrorCode.PROFILE_NOT_FOUND)}
 * 형태로 사용합니다.</p>
 */
public enum ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "요청 값이 올바르지 않습니다."),
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "요청 값 검증에 실패했습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."),

	PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "프로필을 찾을 수 없습니다."),
    MAIN_HERO_NOT_FOUND(HttpStatus.NOT_FOUND, "MAIN_HERO_NOT_FOUND", "메인 소개글을 찾을 수 없습니다."),
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "MENU_NOT_FOUND", "메뉴를 찾을 수 없습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다."),
    POST_SLUG_DUPLICATED(HttpStatus.CONFLICT, "POST_SLUG_DUPLICATED", "이미 사용 중인 게시글 주소입니다."),
    POST_NOT_PUBLISHED(HttpStatus.NOT_FOUND, "POST_NOT_PUBLISHED", "공개된 게시글을 찾을 수 없습니다."),
    POST_SORT_INVALID(HttpStatus.BAD_REQUEST, "POST_SORT_INVALID", "지원하지 않는 게시글 정렬 방식입니다."),
    POST_SIZE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "POST_SIZE_LIMIT_EXCEEDED", "게시글 조회 개수는 최대 50개까지 가능합니다."),
    COMMENT_SIZE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "COMMENT_SIZE_LIMIT_EXCEEDED", "댓글 조회 개수는 최대 50개까지 가능합니다."),
	FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "파일 크기가 너무 큽니다."),
	UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE", "지원하지 않는 파일 형식입니다."),
	FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_UPLOAD_FAILED", "파일 업로드에 실패했습니다.");

	private final HttpStatus httpStatus;
	private final String code;
    private final String message;

	/**
	 * @param httpStatus 클라이언트에 내려줄 HTTP 상태
	 * @param code 프론트엔드가 분기 처리할 안정적인 에러 코드 문자열
	 * @param message 기본 에러 메시지
	 */
	ErrorCode(HttpStatus httpStatus, String code, String message) {
		this.httpStatus = httpStatus;
		this.code = code;
		this.message = message;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	public String getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

}
