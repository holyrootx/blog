package me.jsjlog.blog.common.response;

import org.springframework.validation.FieldError;

public record FieldErrorResponse(
	String field,
	String message,
	Object rejectedValue
) {

	public static FieldErrorResponse from(FieldError fieldError) {
		return new FieldErrorResponse(
			fieldError.getField(),
			fieldError.getDefaultMessage(),
			maskIfSensitive(fieldError.getField(), fieldError.getRejectedValue())
		);
	}

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
