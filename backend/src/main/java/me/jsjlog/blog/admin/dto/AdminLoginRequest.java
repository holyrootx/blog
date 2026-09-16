package me.jsjlog.blog.admin.dto;

/**
 * 관리자 로그인 요청.
 *
 * 비밀번호는 원문으로 받아 서버에서 해시와 비교한다. 화면에서 미리 해시해 보내면
 * 그 해시 자체가 비밀번호가 되어, 가로챈 사람이 원문을 몰라도 로그인할 수 있다.
 */
public record AdminLoginRequest(String username, String password) {
}
