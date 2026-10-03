package me.jsjlog.blog.admin.controller;

import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCommentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CommentRepository commentRepository;

    private Post post;
    private Member member;
    private Member admin;
    private MemberPrincipal memberPrincipal;
    private MemberPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(new Category("관리자 댓글 API", 200L));
        post = new Post("댓글 관리 대상 글", "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now());
        post = postRepository.save(post);

        member = memberRepository.save(Member.ofSocial(
                AuthProvider.NAVER,
                "admin-comment-member",
                "댓글회원",
                null,
                null
        ));
        admin = memberRepository.save(Member.ofLocalAdmin(
                "admin-comment",
                "encoded-password",
                "블로그관리자"
        ));
        memberPrincipal = MemberPrincipal.ofSocial(member);
        adminPrincipal = MemberPrincipal.ofLocal(admin);
    }

    @Test
    @DisplayName("관리자는 댓글을 검색하고 미답변과 숨김 상태를 구분해 조회한다")
    void getCommentsByStatusAndKeyword() throws Exception {
        Comment unanswered = saveComment(null, member, "검색할 미답변 댓글");
        saveComment(unanswered, member, "회원 답글은 관리자 답변으로 세지 않는다");

        Comment answered = saveComment(null, member, "답변이 끝난 댓글");
        saveComment(answered, admin, "관리자 답글");

        Comment hidden = saveComment(null, member, "숨겨진 댓글");
        hidden.delete();

        mockMvc.perform(get("/api/v1/admin/blog/comments")
                        .with(user(adminPrincipal))
                        .param("status", "UNANSWERED")
                        .param("keyword", "검색할"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(unanswered.getId()))
                .andExpect(jsonPath("$.data.items[0].nickname").value("댓글회원"))
                .andExpect(jsonPath("$.data.items[0].answered").value(false))
                .andExpect(jsonPath("$.data.statusCounts.all").value(1))
                .andExpect(jsonPath("$.data.statusCounts.unanswered").value(1))
                .andExpect(jsonPath("$.data.statusCounts.hidden").value(0));

        mockMvc.perform(get("/api/v1/admin/blog/comments")
                        .with(user(adminPrincipal))
                        .param("status", "HIDDEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(hidden.getId()))
                .andExpect(jsonPath("$.data.items[0].hidden").value(true));
    }

    @Test
    @DisplayName("관리자 답글을 등록하면 원댓글은 미답변 목록에서 빠진다")
    void replyToComment() throws Exception {
        Comment comment = saveComment(null, member, "답변을 기다리는 댓글");

        mockMvc.perform(post("/api/v1/admin/blog/comments/{commentId}/replies", comment.getId())
                        .with(user(adminPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"관리자 답변입니다."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNumber());

        mockMvc.perform(get("/api/v1/admin/blog/comments")
                        .with(user(adminPrincipal))
                        .param("status", "UNANSWERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.statusCounts.unanswered").value(0));
    }

    @Test
    @DisplayName("관리자는 댓글을 숨기고 다시 공개할 수 있다")
    void hideAndRestoreComment() throws Exception {
        Comment comment = saveComment(null, member, "공개 상태를 바꿀 댓글");
        String location = "/api/v1/admin/blog/comments/%d/visibility".formatted(comment.getId());

        updateVisibility(location, true);

        mockMvc.perform(get("/api/v1/admin/blog/comments")
                        .with(user(adminPrincipal))
                        .param("status", "HIDDEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].hidden").value(true));

        updateVisibility(location, false);

        mockMvc.perform(get("/api/v1/admin/blog/comments")
                        .with(user(adminPrincipal))
                        .param("status", "HIDDEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("일반 회원은 관리자 댓글 API를 사용할 수 없다")
    void memberCannotUseAdminCommentApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/blog/comments")
                        .with(user(memberPrincipal)))
                .andExpect(status().isForbidden());
    }

    private Comment saveComment(Comment parent, Member author, String content) {
        return commentRepository.save(new Comment(post, parent, author, content));
    }

    private void updateVisibility(String location, boolean hidden) throws Exception {
        mockMvc.perform(put(location)
                        .with(user(adminPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\":%s}".formatted(hidden)))
                .andExpect(status().isOk());
    }
}
