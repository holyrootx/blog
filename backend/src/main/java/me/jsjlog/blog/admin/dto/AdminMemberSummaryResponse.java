package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.MemberStatusCode;

/**
 * 관리자 회원 목록 한 줄.
 *
 * @param status     상태 코드. 화면이 필터와 동작(정지·해제 단추)을 고를 때 쓴다
 * @param statusName 화면에 보일 상태 이름. 공통 코드 MEMBER_STATUS 에서 가져온다
 */
public record AdminMemberSummaryResponse(
        Long id,
        String nickname,
        String email,
        AuthProvider provider,
        MemberStatusCode status,
        String statusName,
        long commentCount,
        LocalDateTime createdAt
) {

    public AdminMemberSummaryResponse withStatusName(String name) {
        return new AdminMemberSummaryResponse(id, nickname, email, provider, status, name, commentCount, createdAt);
    }
}
