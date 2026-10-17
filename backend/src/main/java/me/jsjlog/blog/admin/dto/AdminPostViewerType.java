package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.member.domain.MemberRole;

/**
 * 조회 기록을 남긴 사람의 구분.
 *
 * <p>관리자를 회원과 따로 둔다. 블로그 주인이 자기 글을 열어 본 것은 독자의 조회가 아니라서,
 * 기록에는 남기되 숫자를 셀 때는 갈라 볼 수 있어야 한다.</p>
 */
public enum AdminPostViewerType {
    MEMBER,
    ADMIN,
    ANONYMOUS;

    /** 회원 번호가 남아 있는데 회원 행을 못 찾은 경우도 회원으로 본다 — 비회원으로 바꿔 보이면 안 된다 */
    public static AdminPostViewerType of(Long memberId, MemberRole role) {
        if (memberId == null) {
            return ANONYMOUS;
        }
        return role == MemberRole.ADMIN ? ADMIN : MEMBER;
    }
}
