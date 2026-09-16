package me.jsjlog.blog.admin.domain;

/**
 * 관리자 권한.
 *
 * DB 에는 접두어 없이 이름만 저장한다 (ADMIN). Spring Security 가 비교하는 권한 문자열은
 * ROLE_ 접두어가 붙은 형태라, 그 변환은 {@link #getAuthority()} 한 곳에서만 한다.
 * 양쪽에 각각 적어 두면 한쪽만 고쳤을 때 조용히 권한이 안 맞는다.
 */
public enum AdminRole {

    ADMIN;

    public String getAuthority() {
        return "ROLE_" + name();
    }
}
