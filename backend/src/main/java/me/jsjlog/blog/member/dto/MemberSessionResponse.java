package me.jsjlog.blog.member.dto;

import me.jsjlog.blog.common.security.MemberPrincipal;

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

    public static MemberSessionResponse from(MemberPrincipal principal) {
        return new MemberSessionResponse(
                principal.getId(),
                principal.getNickname(),
                principal.getProfileImageUrl(),
                principal.getRole().getAuthority()
        );
    }
}
