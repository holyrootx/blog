package me.jsjlog.blog.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.member.repository.MemberStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인 → 세션 유지 → 로그아웃까지 실제 요청으로 확인한다.
 *
 * 인증 단위 테스트만으로는 "로그인 응답은 200 인데 다음 요청에서 다시 401" 인 상태를 못 잡는다.
 * 세션에 저장이 되는지는 요청을 두 번 보내 봐야 안다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthFlowTest {

    private static final String USERNAME = "tester";
    private static final String PASSWORD = "test-password";

    private static final String LOGIN = "/api/v1/admin/auth/login";
    private static final String ME = "/api/v1/admin/auth/me";
    private static final String LOGOUT = "/api/v1/admin/auth/logout";
    private static final String ADMIN_API = "/api/v1/admin/blog/categories";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;


    @Autowired

    private MemberStatusHistoryRepository memberStatusHistoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedAdmin() {
        memberStatusHistoryRepository.deleteAll();
        memberRepository.deleteAll();
        memberRepository.save(Member.ofLocalAdmin(USERNAME, passwordEncoder.encode(PASSWORD), "테스트 관리자"));
    }

    private String loginBody(String username, String password) {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
    }

    @Test
    @DisplayName("익명 요청은 공개 API 만 통과한다")
    void anonymous() throws Exception {
        mockMvc.perform(get("/api/v1/blog/categories"))
                .andExpect(status().isOk());

        mockMvc.perform(get(ADMIN_API))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 ADMIN_LOGIN_FAILED")
    void loginFailed() throws Exception {
        mockMvc.perform(post(LOGIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(USERNAME, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_LOGIN_FAILED"));
    }

    @Test
    @DisplayName("없는 아이디도 같은 응답으로 덮인다")
    void unknownUsername() throws Exception {
        // 응답이 달라지면 어떤 아이디가 존재하는지 알려주는 꼴이 된다
        mockMvc.perform(post(LOGIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("someone-else", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_LOGIN_FAILED"));
    }

    @Test
    @DisplayName("로그인하면 세션이 유지되고 관리자 API 가 열린다")
    void loginAndKeepSession() throws Exception {
        MockHttpSession session = login();

        // 다음 요청에서도 로그인 상태여야 한다. 세션 저장을 빠뜨리면 여기서 401 이 난다
        mockMvc.perform(get(ME).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(USERNAME))
                .andExpect(jsonPath("$.data.role").value("ROLE_ADMIN"));

        mockMvc.perform(get(ADMIN_API).session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃하면 세션이 끊긴다")
    void logout() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post(LOGOUT).session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get(ME).session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인하면 세션 ID 가 새로 발급된다")
    void changesSessionId() throws Exception {
        MockHttpSession before = new MockHttpSession();
        String beforeId = before.getId();

        MvcResult result = mockMvc.perform(post(LOGIN).with(csrf())
                        .session(before)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        // 로그인 전에 심어 둔 ID 가 그대로 관리자 세션이 되면 안 된다
        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(beforeId);
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(USERNAME))
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
