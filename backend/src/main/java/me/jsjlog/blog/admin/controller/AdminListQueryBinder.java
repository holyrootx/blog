package me.jsjlog.blog.admin.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;

@ControllerAdvice(assignableTypes = {
		AdminCommentController.class, AdminGuestbookController.class, AdminMemberController.class
})
public class AdminListQueryBinder {
	private static final List<String> COMMON_FIELDS = List.of("page", "size", "status", "keyword");
	private static final List<String> COMMENT_FIELDS = List.of("page", "size", "status", "keyword", "memberId");

	@InitBinder({ "adminCommentSearchCondition", "adminGuestbookSearchCondition", "adminMemberSearchCondition" })
	public void rejectNestedScalarQueries(WebDataBinder binder, HttpServletRequest request) {
		List<String> fields = switch (binder.getObjectName()) {
			case "adminCommentSearchCondition" -> COMMENT_FIELDS;
			case "adminGuestbookSearchCondition", "adminMemberSearchCondition" -> COMMON_FIELDS;
			default -> List.of();
		};
		for (String name : request.getParameterMap().keySet()) {
			for (String field : fields) {
				// scalar 중첩 바인딩 실패만 앞에서 막고, 기존에 무시하던 []와 unknown은 유지한다.
				if ((name.startsWith(field + ".") || name.startsWith(field + "["))
						&& !name.equals(field + "[]")) {
					throw new BlogException(ErrorCode.INVALID_INPUT);
				}
			}
		}
	}
}
