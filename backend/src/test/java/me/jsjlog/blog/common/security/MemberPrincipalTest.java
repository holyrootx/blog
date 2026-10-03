package me.jsjlog.blog.common.security;

import java.lang.reflect.Field;

import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인증 토큰이 principal 에서 이름을 어떻게 뽑는지 확인한다.
 *
 * <p>회귀 테스트다. {@code MemberPrincipal} 이 {@code UserDetails} 와 {@code OAuth2User} 를
 * 같이 구현하는데, Spring Security 는 {@code UserDetails} 쪽을 먼저 본다. 소셜 회원은
 * 아이디가 없어 그 자리가 비었고, 그래서 로그인이 인증 직후 500 으로 끝났다
 * ({@code principalName cannot be empty}).</p>
 */
class MemberPrincipalTest {

    @Test
    @DisplayName("소셜 회원의 인증 이름은 회원 번호다 — 비어 있으면 로그인이 깨진다")
    void socialPrincipalHasNonEmptyAuthenticationName() {
        Member member = socialMember(42L);
        MemberPrincipal principal = MemberPrincipal.ofSocial(member);

        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "google"
        );

        // OAuth2AuthorizedClient 가 이 값을 principalName 으로 받는다
        assertThat(token.getName()).isEqualTo("42");
        assertThat(token.getName()).isNotBlank();
    }

    @Test
    @DisplayName("아이디가 있으면 그대로 쓴다")
    void localPrincipalKeepsUsername() {
        Member member = Member.ofLocalAdmin("tjdwn3377", "hash", "정성주");
        setId(member, 1L);

        assertThat(MemberPrincipal.ofLocal(member).getUsername()).isEqualTo("tjdwn3377");
    }

    @Test
    @DisplayName("getName 은 언제나 회원 번호다 — 제공자 식별자가 감사 컬럼으로 새지 않게")
    void nameIsAlwaysMemberId() {
        assertThat(MemberPrincipal.ofSocial(socialMember(7L)).getName()).isEqualTo("7");
    }

    private static Member socialMember(Long id) {
        Member member = Member.ofSocial(
                AuthProvider.GOOGLE,
                "108712345678901234567",
                "정성주",
                "tjdwn1171@gmail.com",
                "https://lh3.googleusercontent.com/a/abc"
        );

        setId(member, id);

        return member;
    }

    /** 식별자는 DB 가 넣어 주는 값이라 테스트에서는 직접 채운다 */
    private static void setId(Member member, Long id) {
        try {
            Field field = Member.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(member, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
