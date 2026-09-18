package me.jsjlog.blog.member.domain;

/**
 * 회원 권한.
 *
 * <p>관리자와 일반 회원을 한 테이블에 두기로 했으므로, 이 값 하나가 블로그 주인과
 * 방문자를 가른다. 그래서 <b>요청에서 절대 받지 않는다</b> — 어떤 DTO 에도 role 필드를
 * 만들지 않고, 가입할 때 서버가 {@link #USER} 를 넣는다.</p>
 *
 * <p>{@link #ADMIN} 으로 올리는 경로는 코드에 두지 않는다. DB 를 직접 고치는 것만 허용한다.
 * 올리는 기능이 없으면 그 기능의 버그로 뚫릴 일도 없다.</p>
 *
 * <p>DB 에는 접두어 없이 이름만 저장한다. Spring Security 가 비교하는 문자열에는 ROLE_ 이
 * 붙어야 하고, 그 변환은 {@link #getAuthority()} 한 곳에서만 한다.</p>
 */
public enum MemberRole {

    USER,
    ADMIN;

    public String getAuthority() {
        return "ROLE_" + name();
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
