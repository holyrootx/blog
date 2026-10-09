package me.jsjlog.blog.common.security;

import java.util.UUID;

import me.jsjlog.blog.admin.service.AdminMemberService;
import me.jsjlog.blog.common.security.oauth.OAuth2LoginSuccessHandler;
import me.jsjlog.blog.common.security.oauth.OAuthLoginState;
import me.jsjlog.blog.common.security.oauth.OAuthUserInfo;
import me.jsjlog.blog.common.security.oauth.PendingOAuthPrincipal;
import me.jsjlog.blog.common.security.oauth.PendingOAuthSession;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.member.service.MemberService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pending_session_isolation;MODE=MySQL;DB_CLOSE_DELAY=-1")
class PendingOAuthSessionIsolationTest {

    @Autowired MemberRepository members;
    @Autowired MemberService memberService;
    @Autowired AdminMemberService adminMembers;
    @Autowired OAuth2LoginSuccessHandler oauthSuccess;

    @ParameterizedTest
    @CsvSource({
            "SIGNUP_REQUIRED,suspend", "SIGNUP_REQUIRED,withdraw",
            "REACTIVATION_REQUIRED,suspend", "REACTIVATION_REQUIRED,withdraw"
    })
    void previousAccountRevocationPreservesTheNewAccountsPendingSession(OAuthLoginState state, String mutation)
            throws Exception {
        Member previous = members.saveAndFlush(Member.ofSocial(
                AuthProvider.GOOGLE, UUID.randomUUID().toString(), "이전 회원", null, null));
        MockHttpSession browser = new MockHttpSession();
        MockHttpSession otherBrowser = new MockHttpSession();
        completeOAuth(browser, MemberPrincipal.ofSocial(previous));
        completeOAuth(otherBrowser, MemberPrincipal.ofSocial(previous));

        var nextIdentity = new OAuthUserInfo(AuthProvider.GOOGLE, UUID.randomUUID().toString(), "다음 회원", null, null);
        var pending = state == OAuthLoginState.SIGNUP_REQUIRED
                ? PendingOAuthPrincipal.signup(nextIdentity, "다음 회원")
                : PendingOAuthPrincipal.reactivation(nextIdentity, "다음 회원");
        completeOAuth(browser, pending);
        assertThat(browser.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();

        if (mutation.equals("suspend")) adminMembers.suspend(previous.getId(), null);
        else memberService.withdraw(previous.getId());

        assertThat(otherBrowser.isInvalid()).isTrue();
        assertThat(browser.isInvalid()).isFalse();
        var stored = (PendingOAuthSession) browser.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME);
        assertThat(stored.providerUserId()).isEqualTo(nextIdentity.providerUserId());
        assertThat(stored.loginState()).isEqualTo(state);

        // 가입 대기를 마친 새 계정은 자기 계정의 폐기 대상으로 다시 등록된다.
        Member next = members.saveAndFlush(Member.ofSocial(
                AuthProvider.GOOGLE, nextIdentity.providerUserId(), "다음 회원", null, null));
        completeOAuth(browser, MemberPrincipal.ofSocial(next));
        assertThat(browser.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME)).isNull();
        adminMembers.suspend(next.getId(), null);
        assertThat(browser.isInvalid()).isTrue();
    }

    private void completeOAuth(MockHttpSession session, OAuth2User principal) throws Exception {
        var request = new MockHttpServletRequest();
        request.setSession(session);
        var response = new MockHttpServletResponse();
        var authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            new HttpSessionSecurityContextRepository().saveContext(context, request, response);
            oauthSuccess.onAuthenticationSuccess(request, response, authentication);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
