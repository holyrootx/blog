package me.jsjlog.blog.common.security;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.jsjlog.blog.admin.service.AdminMemberService;
import me.jsjlog.blog.common.security.oauth.OAuth2LoginSuccessHandler;
import me.jsjlog.blog.common.security.oauth.OAuthLoginState;
import me.jsjlog.blog.common.security.oauth.PendingOAuthSession;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberStatusCode;
import me.jsjlog.blog.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:session_policy;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "server.servlet.session.timeout=37m"
})
@AutoConfigureMockMvc
class MemberSessionPolicyTest {

    private static final String ADMIN_ME = "/api/v1/admin/auth/me";
    private static final String MEMBER_ME = "/api/v1/auth/me";
    private static final String COMMENTS = "/api/v1/auth/me/comments";
    private static final String PASSWORD = "session-policy-password";

    @Autowired MockMvc mvc;
    @MockitoSpyBean MemberRepository members;
    @Autowired PasswordEncoder passwords;
    @Autowired MemberSessionManager sessions;
    @Autowired OAuth2LoginSuccessHandler oauthSuccess;
    @Autowired AdminMemberService adminMembers;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private Member admin;
    private Member reader;

    @BeforeEach
    void seed() {
        String unique = UUID.randomUUID().toString();
        admin = members.saveAndFlush(Member.ofLocalAdmin("admin-" + unique, passwords.encode(PASSWORD), "관리자"));
        reader = members.saveAndFlush(Member.ofSocial(AuthProvider.GOOGLE, unique, "독자", null, null));
    }

    @Test
    void adminGetsTwoHoursAndSocialLoginDoesNotInheritIt() throws Exception {
        MockHttpSession session = adminLogin(PASSWORD);
        assertThat(session.getMaxInactiveInterval()).isEqualTo(7200);
        mvc.perform(get(ADMIN_ME).session(session)).andExpect(status().isOk());
        socialLogin(reader, session);
        assertThat(session.getMaxInactiveInterval()).isEqualTo(37 * 60);
        mvc.perform(get(MEMBER_ME).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(reader.getId()));
        mvc.perform(get(ADMIN_ME).session(session)).andExpect(status().isForbidden());
    }

    @Test
    void passwordChangeRevokesOldSessionsWithoutRevokingFreshLogin() throws Exception {
        MockHttpSession first = adminLogin(PASSWORD);
        MockHttpSession second = adminLogin(PASSWORD);
        admin.changePasswordHash(passwords.encode("changed-password"));
        members.saveAndFlush(admin);
        MockHttpSession fresh = adminLogin("changed-password");

        assertThat(first.isInvalid()).isTrue();
        assertThat(second.isInvalid()).isTrue();
        mvc.perform(get(ADMIN_ME).session(first)).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN_ME).session(fresh)).andExpect(status().isOk());
    }

    @Test
    void roleChangeIsDetectedOnTheNextProtectedRequest() throws Exception {
        MockHttpSession first = adminLogin(PASSWORD);
        MockHttpSession second = adminLogin(PASSWORD);
        jdbc.update("update member set role = 'USER' where id = ?", admin.getId());
        mvc.perform(get(ADMIN_ME).session(first)).andExpect(status().isUnauthorized());
        assertThat(first.isInvalid()).isTrue();
        assertThat(second.isInvalid()).isTrue();
    }

    @Test
    void suspendedSessionsStayRevokedAfterUnsuspension() throws Exception {
        MockHttpSession first = socialLogin(reader);
        MockHttpSession second = socialLogin(reader);
        MemberPrincipal latePrincipal = MemberPrincipal.ofSocial(reader);
        MockHttpSession administrator = adminLogin(PASSWORD);
        mvc.perform(post(memberAdminPath("suspend")).session(administrator).with(csrf()))
                .andExpect(status().isOk());
        assertThat(first.isInvalid()).isTrue();
        assertThat(second.isInvalid()).isTrue();
        mvc.perform(post(memberAdminPath("unsuspend")).session(administrator).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(get(COMMENTS).session(second)).andExpect(status().isUnauthorized());
        mvc.perform(get(MEMBER_ME).session(first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        MockHttpServletRequest lateRequest = new MockHttpServletRequest();
        assertThatThrownBy(() -> sessions.onAuthentication(lateRequest, latePrincipal))
                .isInstanceOf(SessionAuthenticationException.class);
        mvc.perform(get(COMMENTS).session(socialLogin(members.findById(reader.getId()).orElseThrow())))
                .andExpect(status().isOk());
    }

    @Test
    void withdrawalRevokesBothSessionsAndReactivationDoesNotRestoreOldOnes() throws Exception {
        MockHttpSession first = socialLogin(reader);
        MockHttpSession second = socialLogin(reader);
        mvc.perform(post("/api/v1/auth/withdraw").session(first).with(csrf()))
                .andExpect(status().isOk());
        assertThat(first.isInvalid()).isTrue();
        assertThat(second.isInvalid()).isTrue();
        MockHttpSession recovered = pendingSession(reader.getProviderUserId(), OAuthLoginState.REACTIVATION_REQUIRED);
        assertCompletedOAuth("reactivate", recovered, null);
        mvc.perform(get(COMMENTS).session(recovered)).andExpect(status().isOk());
        mvc.perform(get(COMMENTS).session(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void rollbackDoesNotRevokeHealthySessions() throws Exception {
        MockHttpSession first = socialLogin(reader);
        MockHttpSession second = socialLogin(reader);
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            adminMembers.suspend(reader.getId(), admin.getId());
            transaction.setRollbackOnly();
        });
        assertThat(members.findById(reader.getId()).orElseThrow().getStatusCode()).isEqualTo(MemberStatusCode.ACTIVE);
        assertThat(first.isInvalid()).isFalse();
        mvc.perform(get(COMMENTS).session(second)).andExpect(status().isOk());
    }

    @Test
    void loginRegisteredBeforeSuspensionCommitIsAlsoRevoked() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            MockHttpSession concurrentLogin = new TransactionTemplate(transactions).execute(transaction -> {
                adminMembers.suspend(reader.getId(), admin.getId());
                try {
                    return executor.submit(() -> socialLogin(reader)).get(5, TimeUnit.SECONDS);
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            });
            assertThat(concurrentLogin.isInvalid()).isTrue();
            mvc.perform(get(COMMENTS).session(concurrentLogin)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void profileEditsKeepExistingSessionsAndShowCurrentNickname() throws Exception {
        MockHttpSession first = socialLogin(reader);
        MockHttpSession second = socialLogin(reader);
        Member before = members.findById(reader.getId()).orElseThrow();
        mvc.perform(put("/api/v1/auth/me/nickname").session(first).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"새 이름\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(MEMBER_ME).session(second))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("새 이름"));
        assertThat(first.isInvalid()).isFalse();
        assertThat(second.isInvalid()).isFalse();
        Member after = members.findById(reader.getId()).orElseThrow();
        assertThat(after.getUpdatedAt()).isAfter(before.getUpdatedAt());
        assertThat(after.getUpdatedBy()).isEqualTo(String.valueOf(reader.getId()));
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThat(after.getCreatedBy()).isEqualTo(before.getCreatedBy());
    }

    @Test
    void missingRegistrationIsRecoveredByAnAuthenticatedRequest() throws Exception {
        MockHttpSession session = new MockHttpSession();
        storeSocialAuthentication(reader, session);
        mvc.perform(get(COMMENTS).session(session)).andExpect(status().isOk());
        adminMembers.suspend(reader.getId(), admin.getId());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void deletedAccountBecomesAnonymousOnPublicEndpoints() throws Exception {
        MockHttpSession session = socialLogin(reader);
        members.deleteById(reader.getId());
        mvc.perform(get(MEMBER_ME).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/api/v1/blog/categories").session(session)).andExpect(status().isOk());
    }

    @Test
    void databaseFailureDoesNotPermitRequestOrDestroyHealthySession() throws Exception {
        MockHttpSession session = socialLogin(reader);
        doThrow(new DataAccessResourceFailureException("test database unavailable"))
                .when(members).findById(reader.getId());
        mvc.perform(get(COMMENTS).session(session)).andExpect(status().isInternalServerError());
        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void validSessionStillRequiresCsrfAndLogoutRemainsPerSession() throws Exception {
        MockHttpSession first = adminLogin(PASSWORD);
        MockHttpSession second = adminLogin(PASSWORD);
        mvc.perform(post(memberAdminPath("suspend")).session(first)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/auth/logout").session(first).with(csrf())).andExpect(status().isOk());
        assertThat(first.isInvalid()).isTrue();
        mvc.perform(get(ADMIN_ME).session(second)).andExpect(status().isOk());
    }

    @Test
    void signupRotatesSessionAndCsrfAndUsesMemberTimeout() throws Exception {
        MockHttpSession pending = pendingSession(UUID.randomUUID().toString(), OAuthLoginState.SIGNUP_REQUIRED);
        pending.setMaxInactiveInterval(7200);
        assertCompletedOAuth("signup", pending, "{\"nickname\":\"가입 독자\"}");
    }

    @Test
    void rejoinKeepsExplicitChoiceAndRotatesSessionAndCsrf() throws Exception {
        String providerUserId = reader.getProviderUserId();
        reader.withdraw(LocalDateTime.now());
        members.saveAndFlush(reader);
        MockHttpSession pending = pendingSession(providerUserId, OAuthLoginState.REACTIVATION_REQUIRED);
        assertCompletedOAuth("rejoin", pending, "{\"nickname\":\"다시 가입\"}");
        assertThat(members.findById(reader.getId()).orElseThrow().getStatusCode()).isEqualTo(MemberStatusCode.WITHDRAWN);
        assertThat(members.findById(reader.getId()).orElseThrow().getProviderUserId()).isNull();
    }

    private String memberAdminPath(String action) {
        return "/api/v1/admin/blog/members/" + reader.getId() + "/" + action;
    }

    private MockHttpSession adminLogin(String password) throws Exception {
        var result = mvc.perform(post("/api/v1/admin/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest(admin.getUsername(), password))))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession socialLogin(Member member) throws Exception {
        return socialLogin(member, new MockHttpSession());
    }

    private MockHttpSession socialLogin(Member member, MockHttpSession session) throws Exception {
        MockHttpServletRequest request = storeSocialAuthentication(member, session);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            // 제공자 통신만 제외하고 실제 OAuth 성공 핸들러와 이후 API 요청을 연결한다.
            oauthSuccess.onAuthenticationSuccess(request, new MockHttpServletResponse(), authentication);
            return session;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private MockHttpServletRequest storeSocialAuthentication(Member member, MockHttpSession session) {
        MemberPrincipal principal = MemberPrincipal.ofSocial(member);
        var authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        var request = new MockHttpServletRequest();
        request.setSession(session);
        new HttpSessionSecurityContextRepository().saveContext(context, request, new MockHttpServletResponse());
        return request;
    }

    private MockHttpSession pendingSession(String providerId, OAuthLoginState state) {
        var pending = new MockHttpSession();
        pending.setAttribute(PendingOAuthSession.ATTRIBUTE_NAME,
                new PendingOAuthSession(AuthProvider.GOOGLE, providerId, "독자", null, null, state));
        return pending;
    }

    private String csrfToken(MockHttpSession session) throws Exception {
        var response = mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("data").get("token").asString();
    }

    private void assertCompletedOAuth(String action, MockHttpSession session, String body) throws Exception {
        String previousId = session.getId();
        String previousToken = csrfToken(session);
        var request = post("/api/v1/auth/oauth/" + action).session(session)
                .header("X-CSRF-TOKEN", previousToken).contentType(MediaType.APPLICATION_JSON);
        if (body != null) request.content(body);
        mvc.perform(request).andExpect(status().isOk());
        assertThat(session.getId()).isNotEqualTo(previousId);
        assertThat(session.getMaxInactiveInterval()).isEqualTo(37 * 60);
        assertThat(session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME)).isNull();
        assertThat(csrfToken(session)).isNotEqualTo(previousToken);
        mvc.perform(get(MEMBER_ME).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").isNumber());
        mvc.perform(put("/api/v1/auth/me/nickname").session(session).header("X-CSRF-TOKEN", previousToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"옛 토큰\"}"))
                .andExpect(status().isForbidden());
    }

    private record LoginRequest(String username, String password) { }
}
