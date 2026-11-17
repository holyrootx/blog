package me.jsjlog.blog.admin.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;

@ControllerAdvice(assignableTypes = AdminPostController.class)
public class AdminPostListQueryBinder {
	private static final List<String> SCALAR_FIELDS = List.of("page", "size", "status", "categoryId", "keyword", "trash");

	@InitBinder("adminPostSearchCondition")
	public void rejectNestedScalarQueries(HttpServletRequest request) {
		for (String name : request.getParameterMap().keySet()) {
			for (String field : SCALAR_FIELDS) {
				// record의 scalar를 중첩 객체로 만들다 실패하기 전에 입력 경계에서 거절한다.
				// 기존에 무시하던 빈 [] 접미사와 관계없는 쿼리 이름은 그대로 허용한다.
				if ((name.startsWith(field + ".") || name.startsWith(field + "["))
						&& !name.equals(field + "[]")) {
					throw new BlogException(ErrorCode.INVALID_INPUT);
				}
			}
		}
	}
}
