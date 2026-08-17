package me.jsjlog.blog.common.exception;

import java.util.List;

import me.jsjlog.blog.common.response.ErrorResponse;
import me.jsjlog.blog.common.response.FieldErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * 컨트롤러에서 발생한 예외를 API 공통 에러 응답으로 변환하는 전역 처리기입니다.
 *
 * <p>각 컨트롤러가 try-catch를 반복하지 않도록 예외 처리를 한 곳에 모읍니다.
 * 서비스 계층에서 {@link BlogException}을 던지면 여기에서 HTTP status와
 * {@code ErrorResponse}를 만들어 클라이언트에 반환합니다.</p>
 *
 * <p>로그 정책은 다음처럼 둡니다.</p>
 * <ul>
 *     <li>비즈니스 예외, validation 실패: 예상 가능한 실패이므로 warn</li>
 *     <li>알 수 없는 예외: 서버 오류이므로 stack trace 포함 error</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/**
	 * 서비스 규칙상 의도적으로 발생한 예외를 처리합니다.
	 */
	@ExceptionHandler(BlogException.class)
	public ResponseEntity<ErrorResponse> handleBlogException(BlogException exception, HttpServletRequest request) {
		ErrorCode errorCode = exception.getErrorCode();
		log.warn("Business exception occurred. code={}, path={}, message={}",
			errorCode.getCode(),
			request.getRequestURI(),
			exception.getMessage()
		);

		return ResponseEntity
			.status(errorCode.getHttpStatus())
			.body(ErrorResponse.of(errorCode, exception.getMessage(), request.getRequestURI()));
	}

	/**
	 * {@code @Valid @RequestBody} 검증 실패를 처리합니다.
	 *
	 * <p>필드별 에러 목록을 내려주기 때문에 프론트에서 입력칸별 메시지를 표시할 수 있습니다.</p>
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
		MethodArgumentNotValidException exception,
		HttpServletRequest request
	) {
		List<FieldErrorResponse> errors = exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(FieldErrorResponse::from)
			.toList();

		ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
		log.warn("Validation failed. path={}, errorCount={}", request.getRequestURI(), errors.size());

		return ResponseEntity
			.status(errorCode.getHttpStatus())
			.body(ErrorResponse.of(errorCode, errorCode.getMessage(), errors, request.getRequestURI()));
	}

	/**
	 * {@code @RequestParam}, {@code @PathVariable} 등에 걸린 validation 실패를 처리합니다.
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(
		ConstraintViolationException exception,
		HttpServletRequest request
	) {
		ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
		log.warn("Constraint violation occurred. path={}, message={}", request.getRequestURI(), exception.getMessage());

		return ResponseEntity
			.status(errorCode.getHttpStatus())
			.body(ErrorResponse.of(errorCode, exception.getMessage(), request.getRequestURI()));
	}

	/**
	 * JSON 문법 오류나 요청 body 파싱 실패를 처리합니다.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
		HttpMessageNotReadableException exception,
		HttpServletRequest request
	) {
		ErrorCode errorCode = ErrorCode.INVALID_INPUT;
		log.warn("Invalid request body. path={}, message={}", request.getRequestURI(), exception.getMessage());

		return ResponseEntity
			.status(errorCode.getHttpStatus())
			.body(ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI()));
	}

	/**
	 * 존재하는 URL에 지원하지 않는 HTTP method로 접근했을 때 처리합니다.
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupported(
		HttpRequestMethodNotSupportedException exception,
		HttpServletRequest request
	) {
		ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
		log.warn("Method not allowed. method={}, path={}", exception.getMethod(), request.getRequestURI());

		return ResponseEntity
			.status(errorCode.getHttpStatus())
			.body(ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI()));
	}

	/**
	 * 위에서 명시적으로 처리하지 못한 모든 예외를 마지막으로 처리합니다.
	 *
	 * <p>클라이언트에는 일반적인 서버 오류 메시지만 내려주고,
	 * 실제 stack trace는 서버 로그에만 남깁니다.</p>
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(Exception exception, HttpServletRequest request) {
		ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
		log.error("Unexpected exception occurred. path={}", request.getRequestURI(), exception);

		return ResponseEntity
			.status(errorCode.getHttpStatus())
			.body(ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI()));
	}
}
