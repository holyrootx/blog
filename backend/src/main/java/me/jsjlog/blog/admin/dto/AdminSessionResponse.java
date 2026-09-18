package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.common.security.MemberPrincipal;

/**
 * 지금 로그인한 관리자.
 *
 * 화면이 "누가 로그인해 있는지" 보여주는 데 필요한 값만 담는다.
 * MemberPrincipal 을 그대로 내보내지 않는 이유는 거기에 비밀번호 해시가 들어 있어서다.
 */
public record AdminSessionResponse(String username, String role) {

    public static AdminSessionResponse from(MemberPrincipal principal) {
        return new AdminSessionResponse(principal.getUsername(), principal.getRole().getAuthority());
    }
}
