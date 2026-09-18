package me.jsjlog.blog.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 신규 소셜 회원 가입 요청.
 *
 * 제공자와 제공자 회원 번호는 요청으로 받지 않는다. OAuth 인증 뒤 서버 세션에 보관한 값을 쓴다.
 */
public record OAuthSignupRequest(
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 50, message = "닉네임은 50자까지 입력할 수 있습니다.")
        String nickname
) {
}
