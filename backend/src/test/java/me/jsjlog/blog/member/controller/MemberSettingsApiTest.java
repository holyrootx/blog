package me.jsjlog.blog.member.controller;

import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberStatusCode;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.member.repository.MemberStatusHistoryRepository;
import me.jsjlog.blog.member.repository.MemberStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 내 설정 — 닉네임 변경, 내 댓글, 탈퇴.
 *
 * <p>가입 화면이 "나중에 바꿀 수 있습니다" 라고 알리고, 개인정보처리방침이 탈퇴 시 파기를
 * 약속한다. 그 두 약속이 실제로 지켜지는지 확인하는 테스트다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class MemberSettingsApiTest {

    private static final String ME = "/api/v1/auth/me";
    private static final String NICKNAME = "/api/v1/auth/me/nickname";
    private static final String MY_COMMENTS = "/api/v1/auth/me/comments";
    private static final String WITHDRAW = "/api/v1/auth/withdraw";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberStatusRepository memberStatusRepository;

    @Autowired
    private MemberStatusHistoryRepository memberStatusHistoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Member member;

    @BeforeEach
    void seed() {
        // 상태 이력이 회원을 가리키므로 먼저 지운다. 운영에서는 회원을 지우지 않는다
        memberStatusHistoryRepository.deleteAll();
        memberRepository.deleteAll();

        member = memberRepository.save(Member.ofSocial(
                AuthProvider.GOOGLE,
                "108712345678901234567",
                "졸린너구리47",
                "someone@gmail.com",
                "https://lh3.googleusercontent.com/a/abc"
        ));
    }

    private static org.springframework.security.core.Authentication login(Member member) {
        MemberPrincipal principal = MemberPrincipal.ofSocial(member);

        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
    }

    @Test
    @DisplayName("로그인하지 않으면 비어 있다고 답한다 — 오류가 아니다")
    void anonymousMeIsEmpty() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("닉네임을 바꾸면 그 뒤 조회에도 바뀐 이름이 나온다")
    void changeNicknameIsReflectedInSession() throws Exception {
        mockMvc.perform(put(NICKNAME)
                        .with(authentication(login(member)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"  정성주  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("정성주"));

        // principal 은 로그인하던 순간의 사본이라 그대로다.
        // /me 가 DB 를 읽지 않으면 여기서 옛 이름이 나온다
        mockMvc.perform(get(ME).with(authentication(login(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("정성주"));
    }

    @Test
    @DisplayName("빈 닉네임은 거절한다")
    void rejectsBlankNickname() throws Exception {
        mockMvc.perform(put(NICKNAME)
                        .with(authentication(login(member)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로그인하지 않으면 닉네임을 바꿀 수 없다")
    void anonymousCannotChangeNickname() throws Exception {
        mockMvc.perform(put(NICKNAME)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"몰래\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 댓글 목록은 로그인해야 볼 수 있다")
    void myCommentsNeedLogin() throws Exception {
        mockMvc.perform(get(MY_COMMENTS))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(MY_COMMENTS).with(authentication(login(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("탈퇴하면 자격 정보와 프로필 사진이 지워지고, 식별자는 복원 기간 동안 회원 상태로 옮겨지며 닉네임은 남는다")
    void withdrawClearsCredentialsAndMovesIdentityForRestore() throws Exception {
        mockMvc.perform(post(WITHDRAW)
                        .with(authentication(login(member)))
                        .with(csrf()))
                .andExpect(status().isOk());

        Member withdrawn = memberRepository.findById(member.getId()).orElseThrow();

        assertThat(withdrawn.getStatusCode()).isEqualTo(MemberStatusCode.WITHDRAWN);
        assertThat(withdrawn.getEmail()).isNull();
        assertThat(withdrawn.getUsername()).isNull();
        assertThat(withdrawn.getPasswordHash()).isNull();

        assertThat(withdrawn.getProfileImageUrl()).isNull();

        // 식별자는 회원 행에서 빠지고, 복원 기간 동안만 회원 상태에 남는다
        assertThat(withdrawn.getProviderUserId()).isNull();
        var status = memberStatusRepository.findById(member.getId()).orElseThrow();
        assertThat(status.getRestoreProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(status.getRestoreProviderUserId()).isEqualTo("108712345678901234567");
        assertThat(status.getRestoreExpiresAt()).isAfter(status.getStatusChangedAt().plusDays(29));

        // 남은 댓글의 작성자 표시에 쓰인다
        assertThat(withdrawn.getNickname()).isEqualTo("졸린너구리47");

        // 같은 트랜잭션에서 이력이 한 줄 남는다
        assertThat(memberStatusHistoryRepository.findAllByMember_IdOrderByIdAsc(member.getId()))
                .extracting(history -> history.getFromMemberStatusCode() + "->" + history.getToMemberStatusCode()
                        + ":" + history.getReasonCode() + ":" + history.getActorTypeCode())
                .containsExactly("ACTIVE->WITHDRAWN:WITHDRAW:SELF");
    }

    @Test
    @DisplayName("관리자는 탈퇴할 수 없다 — 블로그에 들어갈 사람이 없어진다")
    void adminCannotWithdraw() throws Exception {
        Member admin = memberRepository.save(
                Member.ofLocalAdmin("owner", passwordEncoder.encode("pw"), "블로그 주인"));

        mockMvc.perform(post(WITHDRAW)
                        .with(authentication(login(admin)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_ADMIN_CANNOT_WITHDRAW"));

        assertThat(memberRepository.findById(admin.getId()).orElseThrow().getStatusCode())
                .isEqualTo(MemberStatusCode.ACTIVE);
    }

    @Test
    @DisplayName("CSRF 토큰 없는 탈퇴는 막는다")
    void withdrawNeedsCsrf() throws Exception {
        mockMvc.perform(post(WITHDRAW).with(authentication(login(member))))
                .andExpect(status().isForbidden());

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getStatusCode())
                .isEqualTo(MemberStatusCode.ACTIVE);
    }
}
