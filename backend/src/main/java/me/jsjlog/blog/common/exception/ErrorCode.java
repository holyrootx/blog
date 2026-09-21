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
	// UNAUTHORIZED 와 나누는 이유는 화면이 할 일이 달라서다.
	// UNAUTHORIZED 는 로그인 화면으로 보내야 하고, 이건 지금 보고 있는 로그인 폼에 사유를 적어야 한다
	ADMIN_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "ADMIN_LOGIN_FAILED", "아이디 또는 비밀번호가 올바르지 않습니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
	DATA_CONSTRAINT_VIOLATED(HttpStatus.CONFLICT, "DATA_CONSTRAINT_VIOLATED", "데이터 규칙에 맞지 않아 처리하지 못했습니다."),
	MODIFIED_BY_OTHERS(HttpStatus.CONFLICT, "MODIFIED_BY_OTHERS", "다른 곳에서 먼저 수정되었습니다. 최신 내용을 다시 불러온 뒤 저장해 주세요."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."),

	// 소셜 로그인 도중의 실패다. 사용자가 고칠 수 있는 게 없어서 메시지는 다시 시도하라는 쪽으로 둔다
	OAUTH_PROVIDER_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "OAUTH_PROVIDER_NOT_SUPPORTED", "지원하지 않는 로그인 방법입니다."),
	OAUTH_ACCOUNT_ID_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "OAUTH_ACCOUNT_ID_MISSING", "로그인 정보를 가져오지 못했습니다. 다시 시도해 주세요."),
	OAUTH_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "OAUTH_SESSION_EXPIRED", "로그인 정보가 만료되었습니다. 소셜 로그인을 다시 진행해 주세요."),
	OAUTH_SIGNUP_NOT_ALLOWED(HttpStatus.CONFLICT, "OAUTH_SIGNUP_NOT_ALLOWED", "현재 로그인 정보로는 신규 가입을 진행할 수 없습니다."),
	OAUTH_REACTIVATION_NOT_ALLOWED(HttpStatus.CONFLICT, "OAUTH_REACTIVATION_NOT_ALLOWED", "현재 로그인 정보로는 계정 복구를 진행할 수 없습니다."),

	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다."),
	// 남의 알림 번호를 넣은 경우도 여기로 온다. 있는데 네 것이 아니라고 알려 주면
	// 남의 알림이 존재한다는 사실을 확인해 주는 셈이 된다
	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다."),
	// 관리자는 이 블로그의 주인이다. 탈퇴하면 관리자 화면에 들어갈 사람이 없어지고
	// 되돌리려면 DB 를 직접 고쳐야 한다. 화면에서 실수로 누를 수 있는 자리에 두지 않는다
	MEMBER_ADMIN_CANNOT_WITHDRAW(HttpStatus.CONFLICT, "MEMBER_ADMIN_CANNOT_WITHDRAW", "관리자 계정은 탈퇴할 수 없습니다."),

	IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "이미지를 찾을 수 없습니다."),
	IMAGE_EMPTY(HttpStatus.BAD_REQUEST, "IMAGE_EMPTY", "이미지 파일이 없습니다."),
	IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE", "이미지 용량이 너무 큽니다."),
	IMAGE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "IMAGE_TYPE_NOT_ALLOWED", "지원하지 않는 이미지 형식입니다."),
	IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_UPLOAD_FAILED", "이미지를 저장하지 못했습니다."),
	IMAGE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_DELETE_FAILED", "이미지를 삭제하지 못했습니다."),

	PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "프로필을 찾을 수 없습니다."),
    PROFILE_REQUIRED_VALUE_MISSING(HttpStatus.BAD_REQUEST, "PROFILE_REQUIRED_VALUE_MISSING", "이름, 소개와 직업을 입력해 주세요."),
    PROFILE_VALUE_TOO_LONG(HttpStatus.BAD_REQUEST, "PROFILE_VALUE_TOO_LONG", "프로필 입력값이 허용 길이를 초과했습니다."),
    BLOG_STARTED_AT_FUTURE(HttpStatus.BAD_REQUEST, "BLOG_STARTED_AT_FUTURE", "블로그 시작일은 오늘 이후로 설정할 수 없습니다."),
    MAIN_HERO_NOT_FOUND(HttpStatus.NOT_FOUND, "MAIN_HERO_NOT_FOUND", "메인 소개글을 찾을 수 없습니다."),
    MAIN_HERO_REQUIRED_VALUE_MISSING(HttpStatus.BAD_REQUEST, "MAIN_HERO_REQUIRED_VALUE_MISSING", "대문 제목과 소개를 입력해 주세요."),
    MAIN_HERO_VALUE_TOO_LONG(HttpStatus.BAD_REQUEST, "MAIN_HERO_VALUE_TOO_LONG", "대문 입력값이 허용 길이를 초과했습니다."),
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "MENU_NOT_FOUND", "메뉴를 찾을 수 없습니다."),
    MENU_HAS_ITEMS(HttpStatus.CONFLICT, "MENU_HAS_ITEMS", "속한 항목이 있는 그룹은 삭제할 수 없습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다."),
    POST_SLUG_DUPLICATED(HttpStatus.CONFLICT, "POST_SLUG_DUPLICATED", "이미 사용 중인 게시글 주소입니다."),
    POST_NOT_PUBLISHED(HttpStatus.NOT_FOUND, "POST_NOT_PUBLISHED", "공개된 게시글을 찾을 수 없습니다."),
    POST_SORT_INVALID(HttpStatus.BAD_REQUEST, "POST_SORT_INVALID", "지원하지 않는 게시글 정렬 방식입니다."),
    POST_TITLE_REQUIRED(HttpStatus.BAD_REQUEST, "POST_TITLE_REQUIRED", "제목을 입력해 주세요."),
    POST_TITLE_TOO_LONG(HttpStatus.BAD_REQUEST, "POST_TITLE_TOO_LONG", "제목은 255자까지 입력할 수 있습니다."),
    POST_EXCERPT_TOO_LONG(HttpStatus.BAD_REQUEST, "POST_EXCERPT_TOO_LONG", "요약은 500자까지 입력할 수 있습니다."),
    POST_THUMBNAIL_TOO_LONG(HttpStatus.BAD_REQUEST, "POST_THUMBNAIL_TOO_LONG", "썸네일 주소는 500자까지 입력할 수 있습니다."),
    POST_CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "POST_CONTENT_REQUIRED", "본문이 없어 발행할 수 없습니다."),
    POST_EXCERPT_REQUIRED(HttpStatus.BAD_REQUEST, "POST_EXCERPT_REQUIRED", "요약이 없어 발행할 수 없습니다."),
    POST_ALREADY_PUBLISHED(HttpStatus.CONFLICT, "POST_ALREADY_PUBLISHED", "이미 발행된 글입니다."),
    POST_NOT_PUBLISHED_YET(HttpStatus.CONFLICT, "POST_NOT_PUBLISHED_YET", "발행된 글이 아닙니다."),
    POST_SIZE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "POST_SIZE_LIMIT_EXCEEDED", "게시글 조회 개수는 최대 50개까지 가능합니다."),
    COMMENT_SIZE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "COMMENT_SIZE_LIMIT_EXCEEDED", "댓글 조회 개수는 최대 50개까지 가능합니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다."),
    COMMENT_CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "COMMENT_CONTENT_REQUIRED", "댓글 내용을 입력해 주세요."),
    COMMENT_CONTENT_TOO_LONG(HttpStatus.BAD_REQUEST, "COMMENT_CONTENT_TOO_LONG", "댓글은 1000자까지 입력할 수 있습니다."),
    COMMENT_PARENT_INVALID(HttpStatus.BAD_REQUEST, "COMMENT_PARENT_INVALID", "같은 게시글의 댓글에만 답글을 작성할 수 있습니다."),
    COMMENT_REPLY_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "COMMENT_REPLY_DEPTH_EXCEEDED", "답글에는 다시 답글을 작성할 수 없습니다."),
    COMMENT_DELETED(HttpStatus.CONFLICT, "COMMENT_DELETED", "삭제된 댓글에는 작업할 수 없습니다."),
    COMMENT_REACTION_REQUIRED(HttpStatus.BAD_REQUEST, "COMMENT_REACTION_REQUIRED", "좋아요 또는 싫어요를 선택해 주세요."),
    COMMENT_VISIBILITY_REQUIRED(HttpStatus.BAD_REQUEST, "COMMENT_VISIBILITY_REQUIRED", "댓글 공개 상태를 선택해 주세요."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."),
    CATEGORY_NAME_DUPLICATED(HttpStatus.CONFLICT, "CATEGORY_NAME_DUPLICATED", "이미 사용 중인 카테고리 이름입니다."),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "CATEGORY_IN_USE", "글이 달린 카테고리는 삭제할 수 없습니다."),
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
