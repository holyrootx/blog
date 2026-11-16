package me.jsjlog.blog.post.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;

@ControllerAdvice(assignableTypes = PostController.class)
public class PostListQueryBinder {
	private static final List<String> SCALAR_FIELDS = List.of("page", "size", "categoryId", "sort", "q");

	@InitBinder("postListCondition")
	public void rejectNestedScalarQueries(HttpServletRequest request) {
		for (String name : request.getParameterMap().keySet()) {
			for (String field : SCALAR_FIELDS) {
				// record 생성자 바인딩 뒤 중첩 속성 자동 생성이 실패하기 전에 잘못된 입력만 거절한다.
				// 기존에 무시하던 빈 [] 접미사와 관계없는 쿼리 이름은 그대로 허용한다.
				if ((name.startsWith(field + ".") || name.startsWith(field + "["))
						&& !name.equals(field + "[]")) {
					throw new BlogException(ErrorCode.INVALID_INPUT);
				}
			}
		}
	}
}
