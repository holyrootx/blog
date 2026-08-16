package com.jsj.blog.common.response;

import org.springframework.validation.FieldError;

/**
 * validation 실패 시 어느 필드가 왜 실패했는지 표현하는 응답입니다.
 *
 * <p>예를 들어 프로필 저장 요청에서 {@code displayName}이 비어 있으면,
 * 프론트엔드는 이 응답을 보고 해당 입력칸 아래에 에러 메시지를 표시할 수 있습니다.</p>
 *
 * @param field 문제가 발생한 요청 필드 이름
 * @param message 필드 검증 실패 메시지
 * @param rejectedValue 거절된 값. 민감한 값은 마스킹 처리
 */
public record FieldErrorResponse(
	String field,
	String message,
	Object rejectedValue
) {

	/**
	 * Spring validation의 {@link FieldError}를 API 응답용 DTO로 변환합니다.
	 */
	public static FieldErrorResponse from(FieldError fieldError) {
		return new FieldErrorResponse(
			fieldError.getField(),
			fieldError.getDefaultMessage(),
			maskIfSensitive(fieldError.getField(), fieldError.getRejectedValue())
		);
	}

	/**
	 * validation 에러 응답에 비밀번호, 토큰, 시크릿 같은 값이 그대로 노출되지 않도록 막습니다.
	 */
	private static Object maskIfSensitive(String field, Object rejectedValue) {
		if (rejectedValue == null) {
			return null;
		}

		String lowerField = field.toLowerCase();
		if (lowerField.contains("password") || lowerField.contains("token") || lowerField.contains("secret")) {
			return "******";
		}

		return rejectedValue;
	}
}
