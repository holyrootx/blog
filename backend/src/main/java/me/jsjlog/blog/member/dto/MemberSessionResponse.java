package me.jsjlog.blog.member.dto;

import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.Member;

/**
 * 로그인 완료 후 화면에 필요한 회원 정보.
 *
 * <p>제공자 식별자와 이메일은 담지 않는다. 화면이 쓸 일이 없고, 한 번 내려보내면
 * 브라우저 어디에나 남는다.</p>
 */
public record MemberSessionResponse(
        Long id,
        String nickname,
        /** 동의를 안 받았거나 제공자가 안 주면 null. 화면은 그때 닉네임 첫 글자를 쓴다 */
        String profileImageUrl,
        String role
) {

    /** 로그인 직후. 그 순간의 principal 이 곧 최신이다 */
    public static MemberSessionResponse from(MemberPrincipal principal) {
        return new MemberSessionResponse(
                principal.getId(),
                principal.getNickname(),
                principal.getProfileImageUrl(),
                principal.getRole().getAuthority()
        );
    }

    /**
     * 세션이 이어지는 동안의 조회.
     *
     * principal 은 로그인하던 순간의 사본이라 닉네임을 바꿔도 그대로다. 회원 행에서 읽어야
     * 바꾼 값이 반영된다.
     */
    public static MemberSessionResponse from(Member member) {
        return new MemberSessionResponse(
                member.getId(),
                member.getNickname(),
                member.getProfileImageUrl(),
                member.getRole().getAuthority()
        );
    }
}
