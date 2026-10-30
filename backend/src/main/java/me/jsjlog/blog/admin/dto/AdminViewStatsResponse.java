package me.jsjlog.blog.admin.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 기간 조회 통계 요약. 전부 조회 기록 기준이고 관리자 본인 조회와 검색 봇은 뺀 값이다.
 *
 * @param from               서울 날짜 기준 첫날. 오늘을 포함해 {@code days} 일이다
 * @param to                 서울 날짜 기준 마지막 날(오늘)
 * @param pageViews          기록 건수. 같은 사람이 같은 글을 30분 안에 다시 연 것은 하나다
 * @param uniqueMembers      기록을 남긴 로그인 회원 수. 비회원은 사람 수를 셀 수 없어서 넣지 않는다
 * @param anonymousViews     비회원 방문 건수. 사람 수가 아니다
 * @param excludedAdminViews 뺀 관리자 본인 조회 건수. 화면이 "몇 건 뺐다" 고 알리는 데 쓴다
 * @param excludedBotViews   뺀 검색 봇 기록 건수
 */
public record AdminViewStatsResponse(
		int days,
		LocalDate from,
		LocalDate to,
		OffsetDateTime recordingStartedAt,
		long pageViews,
		long memberViews,
		long anonymousViews,
		long uniqueMembers,
		long excludedAdminViews,
		long excludedBotViews,
		List<AdminDailyViewResponse> daily,
		List<AdminDeviceViewResponse> devices
) {
}
