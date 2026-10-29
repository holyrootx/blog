package me.jsjlog.blog.admin.dto;

import java.time.LocalDate;

/** 서울 날짜 하루의 조회 기록. 기록이 없는 날도 0 으로 채워 준다 — 빠진 날과 0 인 날이 구분되게 */
public record AdminDailyViewResponse(
		LocalDate date,
		long pageViews,
		long memberViews,
		long anonymousViews
) {
}
