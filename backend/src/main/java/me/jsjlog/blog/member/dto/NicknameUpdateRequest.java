package me.jsjlog.blog.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 닉네임 변경 요청.
 *
 * 가입 화면에서 "나중에 바꿀 수 있습니다" 라고 알린 그 기능이다.
 * 중복 검사는 하지 않는다 — 닉네임에 unique 를 걸지 않기로 했고, 식별은 회원 번호가 한다.
 */
public record NicknameUpdateRequest(
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 50, message = "닉네임은 50자까지 입력할 수 있습니다.")
        String nickname
) {
}
