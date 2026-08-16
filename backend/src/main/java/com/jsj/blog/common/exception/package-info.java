/**
 * 서비스 예외와 전역 예외 처리 코드를 모아두는 패키지입니다.
 *
 * <p>각 도메인 서비스는 문제가 생겼을 때 {@code BlogException}과 {@code ErrorCode}를 사용하고,
 * 실제 HTTP 응답 변환은 {@code GlobalExceptionHandler}가 담당합니다.</p>
 */
package com.jsj.blog.common.exception;
