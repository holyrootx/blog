package me.jsjlog.blog.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * member 에 있는 LOCAL ADMIN 계정으로 인증이 되는지 확인한다.
 *
 * 해시를 소스에 박아 두지 않는다. 테스트에서 그때그때 만들어 넣는다 —
 * 박아 두면 저장소에 해시가 남고, 강도를 올렸을 때 같이 안 바뀐다.
 */
@SpringBootTest
class AdminAuthenticationTest {

    private static final String USERNAME = "tester";
    private static final String PASSWORD = "test-password";

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedAdmin() {
        memberRepository.deleteAll();
        memberRepository.save(Member.ofLocalAdmin(USERNAME, passwordEncoder.encode(PASSWORD), "테스트 관리자"));
    }

    @Test
    @DisplayName("아이디와 비밀번호가 맞으면 ROLE_ADMIN 으로 인증된다")
    void authenticate() {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(USERNAME, PASSWORD));

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isInstanceOf(MemberPrincipal.class);

        // Spring Security 7 은 어떤 수단으로 인증했는지도 권한으로 같이 넣는다
        // (비밀번호로 들어오면 FACTOR_PASSWORD). 그래서 정확히 하나인지 보지 않는다
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains(MemberRole.ADMIN.getAuthority());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 인증에 실패한다")
    void wrongPassword() {
        assertThatThrownBy(() -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(USERNAME, "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("없는 아이디도 비밀번호가 틀린 것과 같은 예외로 덮인다")
    void unknownUsername() {
        // 두 경우를 구분해 주면 어떤 아이디가 존재하는지 알려주는 꼴이 된다
        assertThatThrownBy(() -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("someone-else", PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
    }
}
