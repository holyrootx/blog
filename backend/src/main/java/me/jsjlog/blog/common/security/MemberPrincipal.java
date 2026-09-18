package me.jsjlog.blog.common.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.member.domain.MemberStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * 로그인한 회원. 관리자도 회원이다.
 *
 * <p>두 인터페이스를 같이 구현한다. 관리자는 아이디·비밀번호로 들어와서
 * {@link UserDetails} 가 필요하고, 회원은 소셜로 들어와서 {@link OAuth2User} 가 필요하다.
 * 로그인 방법은 둘이지만 로그인한 뒤의 사람은 한 종류라, 인가와 감사 기록이 보는 타입을
 * 하나로 두는 편이 낫다. 나누면 "관리자면 이쪽, 회원이면 저쪽" 분기가 권한 검사마다 생긴다.</p>
 *
 * <p><b>{@link #getName()} 이 회원 번호를 돌려주는 것이 이 클래스의 핵심이다.</b>
 * 소셜 로그인을 Spring Security 기본 구현에 맡기면 이 값이 제공자가 준 식별자(구글의
 * {@code sub}) 가 된다. 제공자 식별자가 애플리케이션 내부 인증 이름이 되지 않도록
 * principal 을 직접 만들고, 감사 컬럼도 {@code AuditingConfig} 가 이 회원 번호를 사용한다.</p>
 *
 * <p>비밀번호 해시를 들고 있으므로 이 객체를 그대로 응답에 담지 않는다.</p>
 */
public class MemberPrincipal implements UserDetails, OAuth2User {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final String nickname;
    private final String profileImageUrl;
    private final MemberRole role;
    private final MemberStatus status;
    private final Map<String, Object> attributes;

    private MemberPrincipal(
            Long id,
            String username,
            String passwordHash,
            String nickname,
            String profileImageUrl,
            MemberRole role,
            MemberStatus status,
            Map<String, Object> attributes
    ) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.role = role;
        this.status = status;
        this.attributes = attributes;
    }

    /** 아이디·비밀번호로 로그인한 경우 */
    public static MemberPrincipal ofLocal(Member member) {
        return new MemberPrincipal(
                member.getId(),
                member.getUsername(),
                member.getPasswordHash(),
                member.getNickname(),
                member.getProfileImageUrl(),
                member.getRole(),
                member.getStatus(),
                Map.of()
        );
    }

    /**
     * 소셜로 로그인한 경우.
     *
     * <p>{@link OAuth2User#getAttributes()} 에 제공자 응답 원본을 그대로 담지 않는다.
     * 그 원본은 세션에 실려 남는데, 우리가 쓰는 값은 이미
     * {@code OAuthAttributeReader} 가 꺼내서 회원 행에 넣었다. 원본을 계속 들고 있으면
     * 세션마다 이메일과 제공자 식별자가 같이 보관된다.</p>
     */
    public static MemberPrincipal ofSocial(Member member) {
        return new MemberPrincipal(
                member.getId(),
                member.getUsername(),
                member.getPasswordHash(),
                member.getNickname(),
                member.getProfileImageUrl(),
                member.getRole(),
                member.getStatus(),
                Map.of(
                        "memberId", member.getId(),
                        "nickname", member.getNickname(),
                        "role", member.getRole().name()
                )
        );
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    /** 제공자가 준 프로필 사진 주소. 동의를 안 받았으면 {@code null} 이다 */
    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public MemberRole getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public boolean isAdmin() {
        return role.isAdmin();
    }

    /**
     * 인증 주체의 내부 이름. 감사 컬럼도 이 회원 번호를 사용한다.
     *
     * 회원 번호를 쓴다. 닉네임은 unique 가 아니고 바뀔 수 있어서 과거 기록이 가리키는
     * 대상이 사라지고, 제공자 식별자는 남기면 안 되는 값이다.
     */
    @Override
    public String getName() {
        return String.valueOf(id);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getAuthority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /**
     * 소셜 회원은 아이디가 없다. 그래도 {@code null} 을 돌려주면 안 된다.
     *
     * <p>Spring Security 의 {@code AbstractAuthenticationToken.getName()} 은 principal 이
     * {@link UserDetails} 이면 {@link #getName()} 을 보지 않고 <b>이 메서드를 먼저</b> 쓴다.
     * 두 인터페이스를 같이 구현하고 있어서 그 경로가 항상 이긴다.</p>
     *
     * <p>그래서 여기가 비면 소셜 로그인이 인증 직후 500 으로 끝난다 —
     * {@code OAuth2AuthorizedClient} 가 principalName 이 비었다며 거부한다.
     * 아이디가 없을 때 회원 번호를 쓰면 인증 이름이 채워진다. 로컬 관리자는 로그인
     * 아이디를 반환하지만, 감사 컬럼은 {@code AuditingConfig} 가 회원 번호를 직접 읽는다.</p>
     */
    @Override
    public String getUsername() {
        return username != null ? username : String.valueOf(id);
    }

    /** 정지·탈퇴한 계정은 아이디·비밀번호 인증을 통과하지 못한다 */
    @Override
    public boolean isEnabled() {
        return status.isActive();
    }

    /** 비밀번호 해시는 찍지 않는다 */
    @Override
    public String toString() {
        return "MemberPrincipal{id=%d, role=%s, status=%s}".formatted(id, role, status);
    }
}
