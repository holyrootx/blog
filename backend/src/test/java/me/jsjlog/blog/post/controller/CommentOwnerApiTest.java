package me.jsjlog.blog.post.controller;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 내 댓글 고치기·지우기.
 *
 * <p>가장 중요한 것은 <b>남의 댓글에 손대지 못하는 것</b>이다. 여기가 새면 아무나 남이
 * 한 말을 다른 말로 바꿔 놓을 수 있다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentOwnerApiTest {

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
    private Comment comment;
    private MemberPrincipal owner;
    private MemberPrincipal stranger;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(new Category("내 댓글", 920L));

        post = new Post("내 댓글 글", "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now().minusDays(1));
        post = postRepository.save(post);

        Member ownerMember = member("owner-member", "주인");
        Member strangerMember = member("stranger-member", "남");

        owner = MemberPrincipal.ofSocial(ownerMember);
        stranger = MemberPrincipal.ofSocial(strangerMember);

        comment = commentRepository.save(new Comment(post, null, ownerMember, "처음 쓴 내용"));
    }

    private Member member(String providerId, String nickname) {
        return memberRepository.save(Member.ofSocial(
                AuthProvider.NAVER,
                providerId,
                nickname,
                null,
                null
        ));
    }

    private String url() {
        return "/api/v1/blog/comments/%d".formatted(comment.getId());
    }

    @Test
    @DisplayName("내 댓글을 고칠 수 있다")
    void updatesOwnComment() throws Exception {
        mockMvc.perform(put(url())
                        .with(user(owner))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"고친 내용"}
                                """))
                .andExpect(status().isOk());

        assertThat(commentRepository.findById(comment.getId()))
                .get()
                .satisfies(found -> assertThat(found.getContent()).isEqualTo("고친 내용"));
    }

    /**
     * updatedAt 으로 판단하면 안 되는 이유가 여기 있다. 그 값은 관리자가 숨기거나
     * 지울 때도 바뀌어서, 손대지 않은 댓글에 "수정됨" 이 붙는다.
     */
    @Test
    @DisplayName("고치면 수정됨 표시가 붙고, 고치기 전에는 안 붙는다")
    void marksEdited() throws Exception {
        String comments = "/api/v1/blog/posts/%d/comments".formatted(post.getId());

        mockMvc.perform(get(comments))
                .andExpect(jsonPath("$.data.items[0].edited").value(false));

        mockMvc.perform(put(url())
                        .with(user(owner))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"고친 내용"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get(comments))
                .andExpect(jsonPath("$.data.items[0].edited").value(true));
    }

    @Test
    @DisplayName("남의 댓글은 고칠 수 없다")
    void rejectsUpdateByStranger() throws Exception {
        mockMvc.perform(put(url())
                        .with(user(stranger))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"남이 고친 내용"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_MINE"));

        assertThat(commentRepository.findById(comment.getId()))
                .get()
                .satisfies(found -> assertThat(found.getContent()).isEqualTo("처음 쓴 내용"));
    }

    @Test
    @DisplayName("빈 내용으로는 고칠 수 없다")
    void rejectsEmptyContent() throws Exception {
        mockMvc.perform(put(url())
                        .with(user(owner))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_CONTENT_REQUIRED"));
    }

    @Test
    @DisplayName("내 댓글을 지울 수 있다")
    void deletesOwnComment() throws Exception {
        mockMvc.perform(delete(url())
                        .with(user(owner))
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(commentRepository.findById(comment.getId()))
                .get()
                .satisfies(found -> assertThat(found.isDeleted()).isTrue());
    }

    @Test
    @DisplayName("남의 댓글은 지울 수 없다")
    void rejectsDeleteByStranger() throws Exception {
        mockMvc.perform(delete(url())
                        .with(user(stranger))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_MINE"));

        assertThat(commentRepository.findById(comment.getId()))
                .get()
                .satisfies(found -> assertThat(found.isDeleted()).isFalse());
    }

    @Test
    @DisplayName("로그인하지 않으면 고치거나 지울 수 없다")
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(put(url())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"익명이 고친 내용"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete(url()).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("이미 지운 댓글은 다시 고칠 수 없다")
    void rejectsUpdateAfterDelete() throws Exception {
        mockMvc.perform(delete(url()).with(user(owner)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(put(url())
                        .with(user(owner))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"되살리기"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMENT_DELETED"));
    }

    /**
     * 답글이 달린 댓글을 실제로 지우면 답글이 부모를 잃는다. 그래서 표시만 남긴다.
     */
    @Test
    @DisplayName("답글이 달린 댓글을 지워도 답글은 남는다")
    void keepsRepliesAfterDelete() throws Exception {
        Member replier = member("reply-member", "답글쓴이");
        commentRepository.save(new Comment(post, comment, replier, "답글입니다"));

        mockMvc.perform(delete(url()).with(user(owner)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/blog/posts/%d/comments".formatted(post.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].deleted").value(true))
                .andExpect(jsonPath("$.data.items[0].replies[0].content").value("답글입니다"));
    }
}
