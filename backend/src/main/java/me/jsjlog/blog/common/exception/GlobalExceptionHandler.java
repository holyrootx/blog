package me.jsjlog.blog.common.exception;

import java.util.List;

import me.jsjlog.blog.common.response.ErrorResponse;
import me.jsjlog.blog.common.response.FieldErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
	 * 매핑이 없는 URL 요청을 처리합니다.
	 *
	 * <p>이 핸들러가 없으면 맨 아래 {@code Exception} 핸들러가 잡아서
	 * 오타난 주소가 전부 500으로 나갑니다. 클라이언트는 서버가 고장난 줄 알게 됩니다.</p>
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResourceFound(
		NoResourceFoundException exception,
		HttpServletRequest request
	) {
		ErrorCode errorCode = ErrorCode.NOT_FOUND;
		log.warn("No handler found. path={}", request.getRequestURI());

		return ResponseEntity
			.status(errorCode.getHttpStatus())
			.body(ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI()));
	}

	/**
	 * path 변수나 쿼리 파라미터의 타입이 맞지 않을 때 처리합니다.
	 *
	 * <p>{@code /menus/abc} 처럼 숫자 자리에 글자가 오거나, enum 이름을 잘못 적은 경우입니다.
	 * 보낸 쪽 잘못이므로 400 이어야 합니다.</p>
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(
		MethodArgumentTypeMismatchException exception,
		HttpServletRequest request
	) {
		ErrorCode errorCode = ErrorCode.INVALID_INPUT;
		log.warn("Argument type mismatch. name={}, value={}, path={}",
			exception.getName(),
			exception.getValue(),
			request.getRequestURI()
		);

		return ResponseEntity
			.status(errorCode.getHttpStatus())
			.body(ErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI()));
	}

	/**
	 * DB 제약을 위반했을 때 처리합니다. (unique 중복, 참조 중인 행 삭제 등)
	 *
	 * <p>서비스에서 미리 검사해 구체적인 에러 코드를 주는 것이 원칙이고,
	 * 이 핸들러는 그 검사를 빠뜨렸을 때를 위한 그물입니다. 500 대신 409 를 주어
	 * "서버 고장"이 아니라 "데이터 규칙 위반"임을 알립니다.</p>
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
		DataIntegrityViolationException exception,
		HttpServletRequest request
	) {
		ErrorCode errorCode = ErrorCode.DATA_CONSTRAINT_VIOLATED;
		// 원인을 알아야 서비스에 검사를 추가할 수 있으므로 stack trace 를 남긴다
		log.error("Data constraint violated. path={}", request.getRequestURI(), exception);

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
