package me.jsjlog.blog.post.controller;

import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.*;
import me.jsjlog.blog.post.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentReplyPaginationTest {
    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired PostRepository posts;
    @Autowired MemberRepository members;
    @Autowired CommentRepository comments;
    @Autowired CommentReactionRepository reactions;
    Post article;
    Member member;
    Comment parent;

    @BeforeEach
    void setUp() {
        var category = categories.save(new Category("답글 분할", 900L));
        article = new Post("답글 글", "요약", "본문", category, null, null);
        article.publish(LocalDateTime.now());
        article = posts.save(article);
        member = members.save(Member.ofSocial(AuthProvider.GOOGLE, "reply-paging", "답글회원", null, null));
        parent = comments.save(new Comment(article, null, member, "부모"));
    }

    @Test
    void initialRepliesAreBoundedAndCursorVisitsEveryLiveReplyExactlyOnce() throws Exception {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            var reply = comments.save(new Comment(article, parent, member, "답글 " + i));
            if (i == 12) reply.delete(); else expected.add(reply.getId());
            if (i == 35) reactions.save(new CommentReaction(reply, member, CommentReactionType.LIKE));
        }
        parent.delete();
        comments.flush();
        var first = comments.getCommentPageByPostId(article.getId(), null, 1, member.getId());
        assertThat(first.total()).isEqualTo(59);
        var item = first.items().getFirst();
        assertThat(item.deleted()).isTrue();
        assertThat(item.replies()).hasSize(10);
        assertThat(item.replyHasNext()).isTrue();
        List<Long> actual = new ArrayList<>(item.replies().stream().map(r -> r.id()).toList());
        Long cursor = item.replyNextCursor();
        while (true) {
            var page = comments.getReplyPage(article.getId(), parent.getId(), cursor, 20, member.getId());
            assertThat(page.items().size()).isLessThanOrEqualTo(20);
            for (var reply : page.items()) {
                actual.add(reply.id());
                if (reply.content().equals("답글 35")) {
                    assertThat(reply.likeCount()).isEqualTo(1);
                    assertThat(reply.likedByMe()).isTrue();
                }
            }
            if (!page.hasNext()) break;
            cursor = page.nextCursor();
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        mvc.perform(get(replyUrl()).param("cursor", item.replyNextCursor().toString()).param("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(20)));
        mvc.perform(get(replyUrl()).param("size", "51")).andExpect(status().isBadRequest());
        article.unpublish();
        var privatePage = comments.getCommentPageByPostId(article.getId(), null, 1, null);
        assertThat(privatePage.items()).isEmpty();
        assertThat(privatePage.total()).isZero();
        mvc.perform(get(replyUrl())).andExpect(status().isNotFound());
    }

    @Test
    void replyEndpointRejectsParentFromAnotherPostAndReplyAsParent() throws Exception {
        var other = new Post("다른 글", "요약", "본문", article.getCategory(), null, null);
        other.publish(LocalDateTime.now());
        other = posts.save(other);
        mvc.perform(get("/api/v1/blog/posts/" + other.getId() + "/comments/" + parent.getId() + "/replies"))
                .andExpect(status().isNotFound());
        var reply = comments.save(new Comment(article, parent, member, "답글"));
        mvc.perform(get("/api/v1/blog/posts/" + article.getId() + "/comments/" + reply.getId() + "/replies"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rateLimitReturns429AndDoesNotPersistExtraComment() throws Exception {
        String url = "/api/v1/blog/posts/" + article.getId() + "/comments";
        long before = comments.count();
        for (int i = 0; i < 10; i++) {
            mvc.perform(post(url).with(user(MemberPrincipal.ofSocial(member))).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"새 댓글\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post(url).with(user(MemberPrincipal.ofSocial(member))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"초과 댓글\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("COMMENT_RATE_LIMITED"));
        assertThat(comments.count()).isEqualTo(before + 10);
    }

    String replyUrl() {
        return "/api/v1/blog/posts/" + article.getId() + "/comments/" + parent.getId() + "/replies";
    }
}
