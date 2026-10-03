package me.jsjlog.blog.common.security;

import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF 가 실제로 막고 있는지, 그리고 로그인할 때 토큰이 새로 발급되는지 확인한다.
 *
 * 토큰 회전이 중요한 이유: 로그인 전에 받은 토큰이 로그인 뒤에도 그대로 통하면,
 * 남이 미리 쥐어 준 토큰으로 관리자 요청을 만들 수 있다. 세션 ID 를 새로 발급하는 것과
 * 같은 이유다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CsrfProtectionTest {

    private static final String USERNAME = "csrf-tester";
    private static final String PASSWORD = "test-password";

    private static final String CSRF = "/api/v1/auth/csrf";
    private static final String LOGIN = "/api/v1/admin/auth/login";
    private static final String CATEGORIES = "/api/v1/admin/blog/categories";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedAdmin() {
        memberRepository.deleteAll();
        memberRepository.save(Member.ofLocalAdmin(USERNAME, passwordEncoder.encode(PASSWORD), "테스트 관리자"));
    }

    private record Csrf(String headerName, String token) { }

    private Csrf readCsrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get(CSRF).session(session))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");

        return new Csrf(data.get("headerName").asString(), data.get("token").asString());
    }

    @Test
    @DisplayName("토큰 없이 보낸 로그인은 403 으로 막힌다")
    void loginWithoutToken() throws Exception {
        mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("공개 블로그 GET 은 토큰 없이도 그대로 열려 있다")
    void publicGetIsUnaffected() throws Exception {
        // CSRF 는 상태를 바꾸는 요청에만 걸린다. 여기가 막히면 블로그가 통째로 멈춘다
        mockMvc.perform(get("/api/v1/blog/categories"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인하면 토큰이 새로 발급된다")
    void rotatesTokenOnLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Csrf before = readCsrf(session);

        mockMvc.perform(post(LOGIN)
                        .session(session)
                        .header(before.headerName(), before.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(USERNAME, PASSWORD)))
                .andExpect(status().isOk());

        Csrf after = readCsrf(session);

        assertThat(after.token()).isNotEqualTo(before.token());
    }

    @Test
    @DisplayName("로그인 전 토큰으로는 관리자 요청을 보낼 수 없다")
    void oldTokenIsRejectedAfterLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Csrf before = readCsrf(session);

        mockMvc.perform(post(LOGIN)
                        .session(session)
                        .header(before.headerName(), before.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(USERNAME, PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(post(CATEGORIES)
                        .session(session)
                        .header(before.headerName(), before.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"updatedAt":null,"name":"옛토큰","sortOrder":9100}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인 뒤 새로 받은 토큰이면 관리자 요청이 통과한다")
    void newTokenWorksAfterLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Csrf before = readCsrf(session);

        mockMvc.perform(post(LOGIN)
                        .session(session)
                        .header(before.headerName(), before.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(USERNAME, PASSWORD)))
                .andExpect(status().isOk());

        Csrf after = readCsrf(session);

        mockMvc.perform(post(CATEGORIES)
                        .session(session)
                        .header(after.headerName(), after.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"updatedAt":null,"name":"새토큰","sortOrder":9101}
                                """))
                .andExpect(status().isOk());
    }
}
