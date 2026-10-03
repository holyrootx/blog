package me.jsjlog.blog.common.config;

import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.repository.CategoryRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * created_by 에 누가 찍히는지 확인한다.
 *
 * 로그인해서 만든 것과 로그인 없이 만든 것이 구분되어야 한다.
 * 안 그러면 나중에 "이 카테고리 누가 만들었지"를 되짚을 수 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditingTest {

    private static final String USERNAME = "auditor";
    private static final String PASSWORD = "test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long adminId;

    @BeforeEach
    void seedAdmin() {
        memberRepository.deleteAll();
        Member admin = memberRepository.save(
                Member.ofLocalAdmin(USERNAME, passwordEncoder.encode(PASSWORD), "테스트 관리자"));
        adminId = admin.getId();
    }

    @Test
    @DisplayName("로그인한 관리자가 만들면 회원 번호가 남는다")
    void recordsLoggedInAdmin() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/admin/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(post("/api/v1/admin/blog/categories").with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"updatedAt":null,"name":"감사테스트","sortOrder":9001}
                                """))
                .andExpect(status().isOk());

        Category created = categoryRepository.findAll().stream()
                .filter(category -> "감사테스트".equals(category.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(created.getCreatedBy()).isEqualTo(String.valueOf(adminId));
        assertThat(created.getUpdatedBy()).isEqualTo(String.valueOf(adminId));
    }

    @Test
    @DisplayName("로그인 없이 저장하면 system 이 남는다")
    void recordsSystemWithoutLogin() {
        Category saved = categoryRepository.save(new Category("시스템테스트", 9002L));

        assertThat(saved.getCreatedBy()).isEqualTo("system");
    }
}
