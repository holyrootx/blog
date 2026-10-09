package me.jsjlog.blog.common.security.oauth;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.member.domain.MemberStatusCode;
import me.jsjlog.blog.member.domain.NicknameGenerator;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.member.repository.MemberStatusRepository;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 제공자 통신 이후의 실제 HTTP·세션·DB 경계를 검증한다. 외부 OAuth 서버는 호출하지 않는다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:oauth_signup_integrity;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(OAuthSignupIntegrityTest.ProbeConfiguration.class)
class OAuthSignupIntegrityTest {

    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired MemberStatusRepository memberStatuses;
    @Autowired CategoryRepository categories;
    @Autowired PostRepository posts;
    @Autowired CommentRepository comments;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired LookupProbe lookupProbe;
    @Autowired OAuthAttributeReaders attributeReaders;
    @Autowired NicknameGenerator nicknames;
    @Autowired AuditorAware<String> auditorProvider;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void identicalEmailAndSubjectFromDifferentProvidersDoNotMergeAccounts() throws Exception {
        String subject = UUID.randomUUID().toString();
        String email = subject + "@example.test";
        MockHttpSession google = pending(AuthProvider.GOOGLE, subject, email, OAuthLoginState.SIGNUP_REQUIRED);
        long googleId = successfulMemberId(complete("signup", google, "구글 독자"));
        OAuth2User naverLogin = loadNaverUser(subject, email);
        assertThat(naverLogin).isInstanceOf(PendingOAuthPrincipal.class);
        PendingOAuthPrincipal pendingNaver = (PendingOAuthPrincipal) naverLogin;
        assertThat(pendingNaver.getLoginState()).isEqualTo(OAuthLoginState.SIGNUP_REQUIRED);
        MockHttpSession naver = new MockHttpSession();
        naver.setAttribute(PendingOAuthSession.ATTRIBUTE_NAME, PendingOAuthSession.from(pendingNaver));
        long naverId = successfulMemberId(complete("signup", naver, "네이버 독자"));

        assertThat(googleId).isNotEqualTo(naverId);
        assertIdentity(googleId, AuthProvider.GOOGLE, subject, email);
        assertIdentity(naverId, AuthProvider.NAVER, subject, email);
        assertSignedInAs(google, googleId);
        assertSignedInAs(naver, naverId);
        assertThat(loadNaverUser(subject, email)).isInstanceOfSatisfying(MemberPrincipal.class,
                principal -> assertThat(principal.getId()).isEqualTo(naverId));
        Comment original = commentBy(members.findById(googleId).orElseThrow());
        editComment(naver, original, "다른 제공자의 수정", 403);
        editComment(google, original, "원래 작성자의 수정", 200);
    }

    @ParameterizedTest
    @CsvSource({"signup,SIGNUP_REQUIRED", "reactivate,REACTIVATION_REQUIRED", "rejoin,REACTIVATION_REQUIRED"})
    void browserCannotChooseRoleProviderOrAccount(String action, OAuthLoginState state) throws Exception {
        String subject = UUID.randomUUID().toString();
        String email = subject + "@example.test";
        Member administrator = members.saveAndFlush(Member.ofLocalAdmin("admin-" + subject, "test-hash", "관리자"));
        MockHttpSession session = pending(AuthProvider.KAKAO, subject, email, state);
        if (state == OAuthLoginState.REACTIVATION_REQUIRED) {
            Member withdrawn = Member.ofSocial(AuthProvider.KAKAO, subject, "예전 독자", null, null);
            withdrawn.withdraw(LocalDateTime.now());
            members.saveAndFlush(withdrawn);
        }
        String forged = """
                {"nickname":"독자가 고른 이름","role":"ADMIN","provider":"LOCAL",
                 "providerUserId":"other-account","id":%d,"memberId":%d,
                 "email":"forged@example.test","username":"%s","passwordHash":"forged"}
                """.formatted(administrator.getId(), administrator.getId(), administrator.getUsername());

        MvcResult result = mvc.perform(post("/api/v1/auth/oauth/" + action).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(forged))
                .andExpect(status().isOk()).andReturn();
        long signedInId = successfulMemberId(result);

        assertThat(signedInId).isNotEqualTo(administrator.getId());
        assertIdentity(signedInId, AuthProvider.KAKAO, subject, email);
        assertSignedInAs(session, signedInId);
        mvc.perform(get("/api/v1/admin/auth/me").session(session)).andExpect(status().isForbidden());
        assertThat(members.findById(administrator.getId()).orElseThrow().getRole()).isEqualTo(MemberRole.ADMIN);
        assertThat(members.findByProviderAndProviderUserId(AuthProvider.LOCAL, "other-account")).isEmpty();
    }

    @Test
    void twoSignupsCreateOneIdentityAndOnlySuccessfulRequestGetsAuthentication() throws Exception {
        String subject = UUID.randomUUID().toString();
        MockHttpSession delayed = pending(AuthProvider.GOOGLE, subject, null, OAuthLoginState.SIGNUP_REQUIRED);
        MockHttpSession winner = pending(AuthProvider.GOOGLE, subject, null, OAuthLoginState.SIGNUP_REQUIRED);
        var outcomes = raceAfterFirstLookup(subject, "signup", delayed, "signup", winner);

        long memberId = successfulMemberId(outcomes.winner());
        assertThat(outcomes.delayed().getResponse().getStatus()).isEqualTo(409);
        assertThat(json.readTree(outcomes.delayed().getResponse().getContentAsString()).get("code").asString())
                .isEqualTo("DATA_CONSTRAINT_VIOLATED");
        assertThat(identityCount(subject)).isEqualTo(1);
        assertSignedInAs(winner, memberId);
        assertAnonymous(delayed);
        assertThat(members.findById(memberId).orElseThrow().getNickname()).isEqualTo("먼저 완료");
    }

    @Test
    void failedNewAccountInsertRollsBackDetachmentAndDoesNotAuthenticate() throws Exception {
        String subject = UUID.randomUUID().toString();
        Member old = withdrawnMember(subject);
        Comment original = commentBy(old);
        MockHttpSession session = pending(AuthProvider.GOOGLE, subject, null, OAuthLoginState.REACTIVATION_REQUIRED);
        // 이 테스트의 메모리 DB에만 실패 조건을 둬, 연결 해제 뒤 INSERT 실패를 실제로 발생시킨다.
        jdbc.execute("alter table member add constraint ck_oauth_rejoin_failure check (nickname <> '저장 실패 검증')");
        try {
            MvcResult failed = complete("rejoin", session, "저장 실패 검증");
            assertThat(failed.getResponse().getStatus()).isEqualTo(409);
            // 탈퇴하면 제공자 ID 는 회원 상태의 복원 정보로 옮겨 가 있다. 실패한 새로 만들기가 그 정보를 지우지 않았어야 한다
            assertThat(identityCount(subject)).isZero();
            var stillRestorable = memberStatuses.findRestorable(AuthProvider.GOOGLE, subject, LocalDateTime.now())
                    .orElseThrow();
            assertThat(stillRestorable.getMemberId()).isEqualTo(old.getId());
            assertThat(stillRestorable.getMemberStatusCode()).isEqualTo(MemberStatusCode.WITHDRAWN);
            assertThat(jdbc.queryForObject("select member_id from comment where id = ?", Long.class, original.getId()))
                    .isEqualTo(old.getId());
            assertAnonymous(session);
            assertThat(session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME)).isNotNull();
        } finally {
            jdbc.execute("alter table member drop constraint ck_oauth_rejoin_failure");
        }
        long recoveredId = successfulMemberId(complete("reactivate", session, "미사용"));
        assertThat(recoveredId).isEqualTo(old.getId());
        assertSignedInAs(session, old.getId());
        editComment(session, original, "복구 후 수정", 200);
    }

    @ParameterizedTest
    @CsvSource({"reactivate,reactivate", "rejoin,reactivate", "reactivate,rejoin", "rejoin,rejoin"})
    void staleRecoveryChoiceCannotOverwriteTheCompletedChoiceOrTakeOldComments(
            String delayedAction, String winnerAction) throws Exception {
        String subject = UUID.randomUUID().toString();
        Member old = withdrawnMember(subject);
        Comment original = commentBy(old);
        MockHttpSession delayed = pending(AuthProvider.GOOGLE, subject, null, OAuthLoginState.REACTIVATION_REQUIRED);
        MockHttpSession winner = pending(AuthProvider.GOOGLE, subject, null, OAuthLoginState.REACTIVATION_REQUIRED);
        var outcomes = raceAfterFirstLookup(subject, delayedAction, delayed, winnerAction, winner);

        long memberId = successfulMemberId(outcomes.winner());
        assertThat(outcomes.delayed().getResponse().getStatus()).isEqualTo(409);
        assertThat(json.readTree(outcomes.delayed().getResponse().getContentAsString()).get("code").asString())
                .isEqualTo("OAUTH_REACTIVATION_NOT_ALLOWED");
        assertThat(identityCount(subject)).isEqualTo(1);
        assertThat(members.findByProviderAndProviderUserId(AuthProvider.GOOGLE, subject).orElseThrow().getId())
                .isEqualTo(memberId);
        assertThat(jdbc.queryForObject("select member_id from comment where id = ?", Long.class, original.getId()))
                .isEqualTo(old.getId());
        assertSignedInAs(winner, memberId);
        assertAnonymous(delayed);

        Member previous = members.findById(old.getId()).orElseThrow();
        // 어느 쪽이 이겼든 복원 정보는 한 번만 쓰이고 사라진다
        assertThat(memberStatuses.findById(old.getId()).orElseThrow().getRestoreProviderUserId()).isNull();
        if (winnerAction.equals("reactivate")) {
            assertThat(memberId).isEqualTo(old.getId());
            assertThat(previous.getStatusCode()).isEqualTo(MemberStatusCode.ACTIVE);
            assertThat(previous.getProviderUserId()).isEqualTo(subject);
            editComment(winner, original, "복구한 작성자의 수정", 200);
        } else {
            assertThat(memberId).isNotEqualTo(old.getId());
            assertThat(previous.getStatusCode()).isEqualTo(MemberStatusCode.WITHDRAWN);
            assertThat(previous.getProviderUserId()).isNull();
            editComment(winner, original, "새 계정의 수정", 403);
            assertThat(comments.findById(original.getId()).orElseThrow().getContent()).isEqualTo("예전 댓글");
        }
    }

    private Outcomes raceAfterFirstLookup(String subject, String delayedAction, MockHttpSession delayed,
                                          String winnerAction, MockHttpSession winner) throws Exception {
        LookupGate gate = new LookupGate(subject);
        lookupProbe.gate = gate;

        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> complete(delayedAction, delayed, "늦게 완료"));
            MvcResult firstCompleted;
            try {
                if (!gate.snapshotLoaded.await(10, TimeUnit.SECONDS)) {
                    if (future.isDone()) {
                        var response = future.get().getResponse();
                        throw new AssertionError("조회 전에 요청이 끝남: " + response.getStatus()
                                + " " + response.getContentAsString());
                    }
                    throw new AssertionError("조회 중단 지점에 도착하지 못했습니다.");
                }
                firstCompleted = complete(winnerAction, winner, "먼저 완료");
            } finally {
                gate.resume.countDown();
            }
            return new Outcomes(future.get(10, TimeUnit.SECONDS), firstCompleted);
        } finally {
            lookupProbe.gate = null;
        }
    }

    private Member withdrawnMember(String subject) {
        Member member = Member.ofSocial(AuthProvider.GOOGLE, subject, "예전 독자", null, null);
        member.withdraw(LocalDateTime.now());
        return members.saveAndFlush(member);
    }

    private Comment commentBy(Member author) {
        Category category = categories.saveAndFlush(new Category("가입 검증 " + UUID.randomUUID(), 0L));
        Post post = new Post("계정 소유권 검증", "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now().minusDays(1));
        return comments.saveAndFlush(new Comment(posts.saveAndFlush(post), null, author, "예전 댓글"));
    }

    private MockHttpSession pending(AuthProvider provider, String subject, String email, OAuthLoginState state) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingOAuthSession.ATTRIBUTE_NAME,
                new PendingOAuthSession(provider, subject, "제공자 이름", email, null, state));
        return session;
    }

    private OAuth2User loadNaverUser(String subject, String email) {
        var providerUser = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("response", Map.of("id", subject, "nickname", "제공자 이름", "email", email)), "response");
        var service = new CustomOAuth2UserService(request -> providerUser,
                attributeReaders, members, memberStatuses, nicknames, auditorProvider);
        var registration = ClientRegistration.withRegistrationId("naver")
                .clientId("test-client").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/naver")
                .authorizationUri("https://example.test/authorize").tokenUri("https://example.test/token")
                .userInfoUri("https://example.test/user").userNameAttributeName("response").build();
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "test-token",
                Instant.now(), Instant.now().plusSeconds(60));
        return new TransactionTemplate(transactions).execute(transaction ->
                service.loadUser(new OAuth2UserRequest(registration, token)));
    }

    private MvcResult complete(String action, MockHttpSession session, String nickname) throws Exception {
        return mvc.perform(post("/api/v1/auth/oauth/" + action).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new Signup(nickname))))
                .andReturn();
    }

    private long successfulMemberId(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private void assertIdentity(long id, AuthProvider provider, String subject, String email) {
        Member current = members.findById(id).orElseThrow();
        assertThat(current.getRole()).isEqualTo(MemberRole.USER);
        assertThat(current.getStatusCode()).isEqualTo(MemberStatusCode.ACTIVE);
        assertThat(current.getProvider()).isEqualTo(provider);
        assertThat(current.getProviderUserId()).isEqualTo(subject);
        assertThat(current.getEmail()).isEqualTo(email);
        assertThat(current.getUsername()).isNull();
        assertThat(current.getPasswordHash()).isNull();
    }

    private void assertSignedInAs(MockHttpSession session, long id) throws Exception {
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        assertThat(session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME)).isNull();
    }

    private void assertAnonymous(MockHttpSession session) throws Exception {
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/api/v1/auth/me/comments").session(session)).andExpect(status().isUnauthorized());
    }

    private void editComment(MockHttpSession session, Comment comment, String content, int expectedStatus) throws Exception {
        mvc.perform(put("/api/v1/blog/comments/" + comment.getId()).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new Edit(content))))
                .andExpect(status().is(expectedStatus));
    }

    private int identityCount(String subject) {
        return jdbc.queryForObject("select count(*) from member where provider = 'GOOGLE' and provider_user_id = ?",
                Integer.class, subject);
    }

    private record Outcomes(MvcResult delayed, MvcResult winner) { }
    private record Signup(String nickname) { }
    private record Edit(String content) { }

    @TestConfiguration
    static class ProbeConfiguration {
        @Bean LookupProbe lookupProbe() { return new LookupProbe(); }
    }

    /**
     * 실제 SQL 조회를 마친 요청만 멈춘다. 저장·트랜잭션·예외는 모킹하지 않는다.
     * 가입은 회원 조회 뒤에, 복원·새로 만들기는 복원 정보 조회 뒤에 멈춘다.
     */
    @Aspect
    static class LookupProbe {
        volatile LookupGate gate;

        @Around("execution(* me.jsjlog.blog.member.repository.MemberRepository.findByProviderAndProviderUserId(..))"
                + " || execution(* me.jsjlog.blog.member.repository.MemberStatusRepository.findRestorable(..))")
        Object afterLookup(ProceedingJoinPoint query) throws Throwable {
            Object result = query.proceed();
            LookupGate current = gate;
            if (current != null && query.getArgs()[0] == AuthProvider.GOOGLE
                    && current.subject.equals(query.getArgs()[1]) && current.firstRead.compareAndSet(true, false)) {
                current.snapshotLoaded.countDown();
                if (!current.resume.await(10, TimeUnit.SECONDS)) throw new AssertionError("동시 요청 재개 시간 초과");
            }
            return result;
        }
    }

    static class LookupGate {
        final String subject;
        final CountDownLatch snapshotLoaded = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        final AtomicBoolean firstRead = new AtomicBoolean(true);

        LookupGate(String subject) { this.subject = subject; }
    }
}
