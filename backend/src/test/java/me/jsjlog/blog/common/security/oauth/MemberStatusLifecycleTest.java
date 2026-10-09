package me.jsjlog.blog.common.security.oauth;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import me.jsjlog.blog.admin.service.AdminMemberService;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberStatus;
import me.jsjlog.blog.member.domain.MemberStatusCode;
import me.jsjlog.blog.member.domain.NicknameGenerator;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.member.repository.MemberStatusHistoryRepository;
import me.jsjlog.blog.member.repository.MemberStatusRepository;
import me.jsjlog.blog.member.service.MemberService;
import me.jsjlog.blog.member.service.MemberStatusService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 회원 상태 표와 이력, 탈퇴 후 30일 복원 (KAN-26).
 *
 * <p>소셜 로그인 판단(제공자 응답만 고정)을 직접 부르려고 이 패키지에 둔다.
 * 가입·복원·새로 만들기는 실제 HTTP 요청으로 하고, 소셜 로그인 판단은 제공자 응답만 고정한 채 실제 서비스로 한다.
 * 기간이 지난 상황은 복원 기한을 과거로 당겨서 만든다.</p>
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:member_status_lifecycle;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class MemberStatusLifecycleTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MemberRepository members;
    @Autowired MemberStatusRepository statuses;
    @Autowired MemberStatusHistoryRepository histories;
    @Autowired MemberService memberService;
    @Autowired MemberStatusService memberStatusService;
    @Autowired AdminMemberService adminMembers;
    @Autowired OAuthAttributeReaders attributeReaders;
    @Autowired NicknameGenerator nicknames;
    @Autowired AuditorAware<String> auditorProvider;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("가입하면 상태 표에 활동으로 생기고 이력에 가입이 남는다")
    void signupCreatesActiveStatusAndHistory() throws Exception {
        String subject = subject();
        long id = signup(subject);

        assertThat(statuses.findById(id).orElseThrow().getMemberStatusCode()).isEqualTo(MemberStatusCode.ACTIVE);
        assertThat(historyOf(id)).containsExactly("null->ACTIVE:SIGNUP:SELF:" + id);
    }

    @Test
    @DisplayName("탈퇴 후 30일 안에 같은 계정으로 오면 복원을 묻고, 기한이 지나면 새 가입이다")
    void restoreIsOfferedOnlyWithinThePeriod() throws Exception {
        String subject = subject();
        long id = signup(subject);
        withdraw(id);

        assertThat(loadGoogleUser(subject)).isInstanceOfSatisfying(PendingOAuthPrincipal.class, pending -> {
            assertThat(pending.getLoginState()).isEqualTo(OAuthLoginState.REACTIVATION_REQUIRED);
            assertThat(pending.getSuggestedNickname()).isEqualTo("처음 이름");
        });

        expire(id);
        // 하루 한 번 도는 정리 작업이 아직 돌지 않았어도 기한이 지났으면 새 가입이다
        assertThat(statuses.findById(id).orElseThrow().getRestoreProviderUserId()).isEqualTo(subject);
        assertThat(loadGoogleUser(subject)).isInstanceOfSatisfying(PendingOAuthPrincipal.class,
                pending -> assertThat(pending.getLoginState()).isEqualTo(OAuthLoginState.SIGNUP_REQUIRED));
    }

    @Test
    @DisplayName("복원하면 식별자와 프로필이 회원 행으로 돌아오고 복원 정보는 사라진다")
    void reactivateRestoresIdentity() throws Exception {
        String subject = subject();
        long id = signup(subject);
        withdraw(id);

        MvcResult result = complete("reactivate", pending(subject, OAuthLoginState.REACTIVATION_REQUIRED), "미사용");

        assertThat(memberId(result)).isEqualTo(id);
        Member restored = members.findById(id).orElseThrow();
        assertThat(restored.getStatusCode()).isEqualTo(MemberStatusCode.ACTIVE);
        assertThat(restored.getProviderUserId()).isEqualTo(subject);
        assertThat(restored.getEmail()).isEqualTo(subject + "@example.test");
        MemberStatus status = statuses.findById(id).orElseThrow();
        assertThat(status.getRestoreProvider()).isNull();
        assertThat(status.getRestoreProviderUserId()).isNull();
        assertThat(status.getRestoreExpiresAt()).isNull();
        assertThat(historyOf(id)).containsExactly(
                "null->ACTIVE:SIGNUP:SELF:" + id,
                "ACTIVE->WITHDRAWN:WITHDRAW:SELF:" + id,
                "WITHDRAWN->ACTIVE:REACTIVATE:SELF:" + id);
    }

    @Test
    @DisplayName("새로 만들기를 고르면 예전 회원은 탈퇴 그대로 복원 정보만 지우고, 새 회원이 생긴다")
    void rejoinKeepsOldMemberWithdrawn() throws Exception {
        String subject = subject();
        long oldId = signup(subject);
        withdraw(oldId);

        long newId = memberId(complete("rejoin", pending(subject, OAuthLoginState.REACTIVATION_REQUIRED), "새 이름"));

        assertThat(newId).isNotEqualTo(oldId);
        assertThat(members.findById(oldId).orElseThrow().getStatusCode()).isEqualTo(MemberStatusCode.WITHDRAWN);
        assertThat(statuses.findById(oldId).orElseThrow().getRestoreProviderUserId()).isNull();
        assertThat(members.findById(newId).orElseThrow().getProviderUserId()).isEqualTo(subject);
        assertThat(historyOf(oldId)).endsWith("WITHDRAWN->WITHDRAWN:REJOIN:SELF:" + oldId);
        assertThat(historyOf(newId)).containsExactly("null->ACTIVE:SIGNUP:SELF:" + newId);
    }

    @Test
    @DisplayName("복원을 고르기 전에 기한이 지나면 복원을 거절한다")
    void reactivateAfterThePeriodIsRejected() throws Exception {
        String subject = subject();
        long id = signup(subject);
        withdraw(id);
        MockHttpSession session = pending(subject, OAuthLoginState.REACTIVATION_REQUIRED);
        expire(id);

        MvcResult result = complete("reactivate", session, "미사용");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(json.readTree(result.getResponse().getContentAsString()).get("code").asString())
                .isEqualTo("OAUTH_REACTIVATION_NOT_ALLOWED");
        assertThat(members.findById(id).orElseThrow().getStatusCode()).isEqualTo(MemberStatusCode.WITHDRAWN);
    }

    @Test
    @DisplayName("매일 정리는 기한이 지난 복원 정보만 지우고 이력에 시스템이 남는다")
    void dailyExpiryClearsOnlyExpired() throws Exception {
        long expired = signup(subject());
        long fresh = signup(subject());
        withdraw(expired);
        withdraw(fresh);
        expire(expired);

        int cleared = memberStatusService.expireRestores(LocalDateTime.now());

        assertThat(cleared).isGreaterThanOrEqualTo(1);
        assertThat(statuses.findById(expired).orElseThrow().getRestoreProviderUserId()).isNull();
        assertThat(statuses.findById(fresh).orElseThrow().getRestoreProviderUserId()).isNotNull();
        assertThat(historyOf(expired)).endsWith("WITHDRAWN->WITHDRAWN:RESTORE_EXPIRED:SYSTEM:null");
        assertThat(historyOf(fresh)).noneMatch(line -> line.contains("RESTORE_EXPIRED"));
    }

    @Test
    @DisplayName("기한이 지난 뒤 같은 계정으로 새로 가입하면 묵은 복원 정보를 먼저 지워, 새 계정도 다시 탈퇴할 수 있다")
    void signupAfterExpiryClearsStaleRestore() throws Exception {
        String subject = subject();
        long oldId = signup(subject);
        withdraw(oldId);
        expire(oldId);

        long newId = signup(subject);
        assertThat(statuses.findById(oldId).orElseThrow().getRestoreProviderUserId()).isNull();
        assertThat(historyOf(oldId)).endsWith("WITHDRAWN->WITHDRAWN:RESTORE_EXPIRED:SYSTEM:null");

        withdraw(newId);
        assertThat(statuses.findById(newId).orElseThrow().getRestoreProviderUserId()).isEqualTo(subject);
    }

    @Test
    @DisplayName("관리자 정지와 해제는 처리한 관리자 번호와 함께 이력에 남는다")
    void adminSuspensionRecordsActor() throws Exception {
        long id = signup(subject());
        Member admin = members.saveAndFlush(Member.ofLocalAdmin("admin-" + UUID.randomUUID(), "hash", "관리자"));

        inTransaction(() -> adminMembers.suspend(id, admin.getId()));
        inTransaction(() -> adminMembers.unsuspend(id, admin.getId()));

        assertThat(historyOf(id)).endsWith(
                "ACTIVE->SUSPENDED:SUSPEND:ADMIN:" + admin.getId(),
                "SUSPENDED->ACTIVE:UNSUSPEND:ADMIN:" + admin.getId());
    }

    private long signup(String subject) throws Exception {
        return memberId(complete("signup", pending(subject, OAuthLoginState.SIGNUP_REQUIRED), "처음 이름"));
    }

    private void withdraw(long id) {
        inTransaction(() -> memberService.withdraw(id));
    }

    /** 복원 기한을 과거로 당긴다. 실제로는 탈퇴 후 30일이 지난 상황이다 */
    private void expire(long id) {
        jdbc.update("update member_status set restore_expires_at = ? where member_id = ?",
                LocalDateTime.now().minusMinutes(1), id);
    }

    private List<String> historyOf(long id) {
        return histories.findAllByMember_IdOrderByIdAsc(id).stream()
                .map(history -> history.getFromMemberStatusCode() + "->" + history.getToMemberStatusCode()
                        + ":" + history.getReasonCode() + ":" + history.getActorTypeCode() + ":" + history.getActorMemberId())
                .toList();
    }

    private MockHttpSession pending(String subject, OAuthLoginState state) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingOAuthSession.ATTRIBUTE_NAME, new PendingOAuthSession(
                AuthProvider.GOOGLE, subject, "제공자 이름", subject + "@example.test", null, state));
        return session;
    }

    private MvcResult complete(String action, MockHttpSession session, String nickname) throws Exception {
        return mvc.perform(post("/api/v1/auth/oauth/" + action).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nickname", nickname))))
                .andReturn();
    }

    private long memberId(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private OAuth2User loadGoogleUser(String subject) {
        var providerUser = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", subject, "name", "제공자 이름", "email", subject + "@example.test"), "sub");
        var service = new CustomOAuth2UserService(request -> providerUser,
                attributeReaders, members, statuses, nicknames, auditorProvider);
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

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> work.run());
    }

    private static String subject() {
        return UUID.randomUUID().toString();
    }
}
