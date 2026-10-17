package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.post.domain.DeviceType;

/**
 * 조회 기록을 남긴 사람의 구분.
 *
 * <p>관리자와 검색 봇을 따로 둔다. 블로그 주인이 자기 글을 연 것과 검색 봇이 긁어 간 것은 독자의 조회가
 * 아니라서, 기록에는 남기되 숫자를 셀 때는 갈라 볼 수 있어야 한다.</p>
 */
public enum AdminPostViewerType {
    MEMBER,
    ADMIN,
    ANONYMOUS,
    BOT;

    /** 회원 번호가 남아 있는데 회원 행을 못 찾은 경우도 회원으로 본다 — 비회원으로 바꿔 보이면 안 된다 */
    public static AdminPostViewerType of(Long memberId, MemberRole role, DeviceType deviceType) {
        if (memberId == null) {
            return deviceType == DeviceType.BOT ? BOT : ANONYMOUS;
        }
        return role == MemberRole.ADMIN ? ADMIN : MEMBER;
    }
}
