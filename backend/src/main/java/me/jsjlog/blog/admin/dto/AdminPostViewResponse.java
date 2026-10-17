package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.member.domain.MemberStatusCode;

import java.time.OffsetDateTime;

/**
 * 조회 기록 한 줄.
 *
 * <p>회원은 지금 알아볼 수 있는 최소한만 준다 — 번호, 닉네임, 상태. 이메일은 주지 않는다.
 * 탈퇴한 회원도 닉네임은 남는 정책이라 그대로 보이고, 상태로 탈퇴를 알린다.</p>
 *
 * <p>시각은 시간대를 붙여 준다. 시간대 없는 값은 브라우저가 자기 시간대로 읽어서,
 * 관리자 PC 의 시간대에 따라 다른 시각이 보인다.</p>
 */
public record AdminPostViewResponse(
        Long id,
        OffsetDateTime viewedAt,
        AdminPostViewerType viewerType,
        Long memberId,
        String nickname,
        MemberStatusCode memberStatus,
        String memberStatusName
) {

    public AdminPostViewResponse withMemberStatusName(String name) {
        return new AdminPostViewResponse(id, viewedAt, viewerType, memberId, nickname, memberStatus, name);
    }
}
