package me.jsjlog.blog.post.controller;

import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentApiTest {

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
    private MemberPrincipal principal;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(new Category("댓글 API", 100L));
        post = new Post("댓글 API 글", "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now());
        post = postRepository.save(post);

        member = memberRepository.save(Member.ofSocial(
                AuthProvider.NAVER,
                "comment-api-member",
                "API회원",
                null,
                null
        ));
        principal = MemberPrincipal.ofSocial(member);
    }

    @Test
    @DisplayName("회원은 댓글을 등록하고 반응을 변경한 뒤 취소할 수 있다")
    void memberCommentAndReactionFlow() throws Exception {
        String commentLocation = "/api/v1/blog/posts/%d/comments".formatted(post.getId());

        mockMvc.perform(post(commentLocation)
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"API로 등록한 댓글","parentId":null}
                                """))
                .andExpect(status().isOk());

        Long commentId = commentRepository.findAll().getFirst().getId();
        String reactionLocation = "/api/v1/blog/comments/%d/reaction".formatted(commentId);

        mockMvc.perform(put(reactionLocation)
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"LIKE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.dislikeCount").value(0))
                .andExpect(jsonPath("$.data.likedByMe").value(true))
                .andExpect(jsonPath("$.data.dislikedByMe").value(false));

        mockMvc.perform(put(reactionLocation)
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DISLIKE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.dislikeCount").value(1))
                .andExpect(jsonPath("$.data.likedByMe").value(true))
                .andExpect(jsonPath("$.data.dislikedByMe").value(true));

        mockMvc.perform(get(commentLocation).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].nickname").value("API회원"))
                .andExpect(jsonPath("$.data.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.data.items[0].dislikeCount").value(1))
                .andExpect(jsonPath("$.data.items[0].likedByMe").value(true))
                .andExpect(jsonPath("$.data.items[0].dislikedByMe").value(true));

        mockMvc.perform(delete(reactionLocation + "?type=LIKE")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.dislikeCount").value(1))
                .andExpect(jsonPath("$.data.likedByMe").value(false))
                .andExpect(jsonPath("$.data.dislikedByMe").value(true));

        mockMvc.perform(delete(reactionLocation + "?type=DISLIKE")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.dislikeCount").value(0))
                .andExpect(jsonPath("$.data.likedByMe").value(false))
                .andExpect(jsonPath("$.data.dislikedByMe").value(false));
    }

    @Test
    @DisplayName("로그인하지 않은 방문자는 댓글을 등록할 수 없다")
    void anonymousCannotCreateComment() throws Exception {
        mockMvc.perform(post("/api/v1/blog/posts/%d/comments".formatted(post.getId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"익명 댓글","parentId":null}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
