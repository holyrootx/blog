package me.jsjlog.blog.common.security.oauth;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.jsjlog.blog.admin.service.AdminMemberService;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.common.security.AdminUserDetailsService;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.common.security.MemberSessionManager;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.member.domain.MemberStatus;
import me.jsjlog.blog.member.domain.NicknameGenerator;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/** 제공자 응답만 고정하고 실제 JPA 트랜잭션의 조회와 커밋 순서를 latch로 제어한다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:oauth_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1")
class OAuthSessionConcurrencyTest {

    @Autowired MemberRepository members;
    @Autowired MemberSessionManager sessions;
    @Autowired AdminMemberService adminMembers;
    @Autowired MemberService memberService;
    @Autowired CommentRepository comments;
    @Autowired NicknameGenerator nicknames;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AuditorAware<String> auditorProvider;
    @Autowired JdbcTemplate jdbc;

    private Member reader;
    private MemberRepository pausedMembers;
    private CountDownLatch snapshotLoaded;
    private CountDownLatch continueLogin;

    @BeforeEach
    void seed() {
        reader = members.saveAndFlush(Member.ofSocial(AuthProvider.GOOGLE, UUID.randomUUID().toString(),
                "독자", "before@example.test", "https://example.test/before.png"));
        pausedMembers = mock(MemberRepository.class, withSettings().defaultAnswer(delegatesTo(members)));
        snapshotLoaded = new CountDownLatch(1);
        continueLogin = new CountDownLatch(1);
        doAnswer(invocation -> pauseAfterRead(members.findByProviderAndProviderUserId(
                invocation.getArgument(0), invocation.getArgument(1))))
                .when(pausedMembers).findByProviderAndProviderUserId(AuthProvider.GOOGLE, reader.getProviderUserId());
    }

    @Test
    void adminLookupStartedBeforeRevocationCannotCreateFreshAuthenticationAfterRestore() throws Exception {
        Member admin = members.saveAndFlush(Member.ofLocalAdmin("admin-" + UUID.randomUUID(), "hash", "관리자"));
        doAnswer(invocation -> pauseAfterRead(members.findByUsernameAndRole(admin.getUsername(), MemberRole.ADMIN)))
                .when(pausedMembers).findByUsernameAndRole(admin.getUsername(), MemberRole.ADMIN);

        MemberPrincipal principal = runPaused(
                () -> new TransactionTemplate(transactions).execute(transaction ->
                        new AdminUserDetailsService(pausedMembers).loadUserByUsername(admin.getUsername())),
                () -> {
                    new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                        members.findById(admin.getId()).orElseThrow().suspend();
                        sessions.revokeAfterCommit(admin.getId());
                    });
                    new TransactionTemplate(transactions).executeWithoutResult(transaction ->
                            members.findById(admin.getId()).orElseThrow().unsuspend());
                });

        assertThatThrownBy(() -> sessions.onAuthentication(new MockHttpServletRequest(), principal))
                .isInstanceOf(SessionAuthenticationException.class);
    }

    @Test
    void socialLookupStartedBeforeRevocationCannotCreateFreshAuthenticationAfterRestore() throws Exception {
        OAuth2User principal = runPaused(this::loadSocialUser, () -> {
            adminMembers.suspend(reader.getId());
            adminMembers.unsuspend(reader.getId());
        });
        assertThatThrownBy(() -> sessions.onAuthentication(new MockHttpServletRequest(), (MemberPrincipal) principal))
                .isInstanceOf(SessionAuthenticationException.class);
    }

    @Test
    void normalProfileSynchronizationKeepsCurrentValuesAndAuditFields() {
        jdbc.update("update member set updated_by = 'previous-auditor' where id = ?", reader.getId());
        Member before = members.findById(reader.getId()).orElseThrow();
        continueLogin.countDown();
        MemberPrincipal principal = (MemberPrincipal) loadSocialUser();
        Member current = members.findById(reader.getId()).orElseThrow();
        assertThat(current.getEmail()).isEqualTo("after@example.test");
        assertThat(current.getProfileImageUrl()).isEqualTo("https://example.test/after.png");
        assertThat(principal.getProfileImageUrl()).isEqualTo(current.getProfileImageUrl());
        assertThat(current.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(current.getNickname()).isEqualTo("독자");
        assertThat(current.getUpdatedAt()).isAfter(before.getUpdatedAt());
        assertThat(current.getUpdatedBy()).isEqualTo("system");
        assertThat(current.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThat(current.getCreatedBy()).isEqualTo(before.getCreatedBy());
    }

    @Test
    void profileSynchronizationCannotUndoCommittedSuspension() throws Exception {
        Object outcome = runPaused(this::loadSocialUserOrError, () -> adminMembers.suspend(reader.getId()));
        Member current = members.findById(reader.getId()).orElseThrow();
        assertThat(current).extracting(Member::getStatus, Member::getEmail)
                .containsExactly(MemberStatus.SUSPENDED, "before@example.test");
        assertThat(outcome).isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void profileSynchronizationCannotRestoreWithdrawnAccountOrErasedEmail() throws Exception {
        Object outcome = runPaused(this::loadSocialUserOrError, () -> memberService.withdraw(reader.getId()));
        Member current = members.findById(reader.getId()).orElseThrow();
        assertThat(current).extracting(Member::getStatus, Member::getEmail)
                .containsExactly(MemberStatus.WITHDRAWN, null);
        assertThat(current.getNickname()).isEqualTo("독자");
        assertThat(current.getProfileImageUrl()).isEqualTo("https://example.test/before.png");
        assertThat(current.getProviderUserId()).isEqualTo(reader.getProviderUserId());
        assertThat(current.getUsername()).isNull();
        assertThat(current.getPasswordHash()).isNull();
        assertThat(outcome).isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void nicknameEditCannotRestoreWithdrawnAccountOrErasedEmail() throws Exception {
        doAnswer(invocation -> pauseAfterRead(members.findById(reader.getId())))
                .when(pausedMembers).findById(reader.getId());
        MemberService delayedNicknameService = new MemberService(pausedMembers, sessions, comments, auditorProvider);
        Object outcome = runPaused(() -> {
                    try {
                        return new TransactionTemplate(transactions).execute(transaction ->
                                delayedNicknameService.changeNickname(reader.getId(), "새 이름"));
                    } catch (BlogException rejected) {
                        return rejected;
                    }
                },
                () -> memberService.withdraw(reader.getId()));
        Member current = members.findById(reader.getId()).orElseThrow();
        assertThat(current).extracting(Member::getStatus, Member::getEmail)
                .containsExactly(MemberStatus.WITHDRAWN, null);
        assertThat(current.getNickname()).isEqualTo("독자");
        assertThat(current.getProfileImageUrl()).isEqualTo("https://example.test/before.png");
        assertThat(current.getProviderUserId()).isEqualTo(reader.getProviderUserId());
        assertThat(outcome).isInstanceOf(BlogException.class);
    }

    private Optional<Member> pauseAfterRead(Optional<Member> snapshot) throws InterruptedException {
        snapshotLoaded.countDown();
        if (!continueLogin.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("조회 후 로그인 재개 신호를 받지 못했습니다.");
        }
        return snapshot;
    }

    private <T> T runPaused(Callable<T> login, Runnable concurrentChange) throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(login);
            try {
                assertThat(snapshotLoaded.await(5, TimeUnit.SECONDS)).isTrue();
                concurrentChange.run();
            } finally {
                continueLogin.countDown();
            }
            return future.get(5, TimeUnit.SECONDS);
        }
    }

    private Object loadSocialUserOrError() {
        try {
            return loadSocialUser();
        } catch (OAuth2AuthenticationException rejected) {
            return rejected;
        }
    }

    private OAuth2User loadSocialUser() {
        var providerUser = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of(
                "sub", reader.getProviderUserId(),
                "name", "제공자 이름",
                "email", "after@example.test",
                "picture", "https://example.test/after.png"), "sub");
        var service = new CustomOAuth2UserService(request -> providerUser,
                new OAuthAttributeReaders(List.of(new GoogleAttributeReader())), pausedMembers, nicknames, auditorProvider);
        var registration = ClientRegistration.withRegistrationId("google")
                .clientId("test-client").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/google")
                .authorizationUri("https://example.test/authorize").tokenUri("https://example.test/token")
                .userInfoUri("https://example.test/user").userNameAttributeName("sub").build();
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "test-token",
                Instant.now(), Instant.now().plusSeconds(60));
        return new TransactionTemplate(transactions).execute(transaction ->
                service.loadUser(new OAuth2UserRequest(registration, token)));
    }
}
