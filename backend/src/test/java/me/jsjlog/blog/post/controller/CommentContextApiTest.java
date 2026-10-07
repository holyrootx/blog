package me.jsjlog.blog.post.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.*;
import me.jsjlog.blog.post.dto.CommentReplyResponse;
import me.jsjlog.blog.post.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentContextApiTest {
    @Autowired MockMvc mvc;
    @Autowired PostRepository posts;
    @Autowired CategoryRepository categories;
    @Autowired MemberRepository members;
    @Autowired CommentRepository comments;
    @Autowired CommentReactionRepository reactions;
    @Autowired CommentReportRepository reports;

    Post article;
    Member writer;
    Member viewer;
    Comment parent;

    @BeforeEach
    void setUp() {
        var category = categories.save(new Category("댓글 목적지", 990L));
        article = publishedPost("알림 글", category);
        writer = members.save(Member.ofSocial(AuthProvider.GOOGLE, "context-writer", "작성자", null, null));
        viewer = members.save(Member.ofSocial(AuthProvider.NAVER, "context-viewer", "방문자", null, null));
        parent = comments.save(new Comment(article, null, writer, "오래된 부모 댓글"));
    }

    @Test
    void oldTopLevelCommentCanBeLocatedWithoutWalkingEarlierPages() throws Exception {
        for (int index = 0; index < 65; index++) {
            comments.save(new Comment(article, null, writer, "새 댓글 " + index));
        }
        var page = comments.getCommentPageByPostId(article.getId(), null, 20, null);
        assertThat(page.items()).noneMatch(item -> item.id().equals(parent.getId()));

        mvc.perform(get(contextUrl(article, parent.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetCommentId").value(parent.getId()))
                .andExpect(jsonPath("$.data.item.id").value(parent.getId()))
                .andExpect(jsonPath("$.data.item.content").value("오래된 부모 댓글"))
                .andExpect(jsonPath("$.data.item.mine").value(false));
    }

    @Test
    void distantReplyIsIncludedWhileTheOriginalCursorAndViewerFlagsRemainCorrect() throws Exception {
        List<Comment> replies = new ArrayList<>();
        for (int index = 0; index < 65; index++) {
            replies.add(comments.save(new Comment(article, parent, writer, "답글 " + index)));
        }
        Comment target = replies.get(49);
        reactions.save(new CommentReaction(target, viewer, CommentReactionType.LIKE));
        reports.save(new CommentReport(target, viewer, CommentReportReason.SPAM, null));
        comments.flush();

        mvc.perform(get(contextUrl(article, target.getId())).with(user(MemberPrincipal.ofSocial(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetCommentId").value(target.getId()))
                .andExpect(jsonPath("$.data.item.replies", hasSize(11)))
                .andExpect(jsonPath("$.data.item.replies[10].id").value(target.getId()))
                .andExpect(jsonPath("$.data.item.replies[10].likeCount").value(1))
                .andExpect(jsonPath("$.data.item.replies[10].likedByMe").value(true))
                .andExpect(jsonPath("$.data.item.replies[10].reportedByMe").value(true))
                .andExpect(jsonPath("$.data.item.replies[10].mine").value(false))
                .andExpect(jsonPath("$.data.item.replyNextCursor").value(replies.get(9).getId()))
                .andExpect(jsonPath("$.data.item.replyHasNext").value(true));

        var context = comments.getCommentContext(article.getId(), target.getId(), writer.getId()).orElseThrow();
        assertThat(context.item().mine()).isTrue();
        assertThat(context.item().replies()).allMatch(CommentReplyResponse::mine);
        var seen = new LinkedHashSet<>(context.item().replies().stream().map(CommentReplyResponse::id).toList());
        Long cursor = context.item().replyNextCursor();
        while (true) {
            var page = comments.getReplyPage(article.getId(), parent.getId(), cursor, 20, null);
            page.items().forEach(reply -> seen.add(reply.id()));
            if (!page.hasNext()) break;
            cursor = page.nextCursor();
        }
        assertThat(seen).containsExactlyInAnyOrderElementsOf(replies.stream().map(Comment::getId).toList());
        mvc.perform(get(contextUrl(article, target.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.replies[10].likedByMe").value(false))
                .andExpect(jsonPath("$.data.item.replies[10].reportedByMe").value(false));
    }

    @Test
    void replyAlreadyOnTheFirstPageIsNotDuplicated() throws Exception {
        Comment target = null;
        for (int index = 0; index < 15; index++) {
            var reply = comments.save(new Comment(article, parent, writer, "답글 " + index));
            if (index == 3) target = reply;
        }
        mvc.perform(get(contextUrl(article, target.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.replies", hasSize(10)))
                .andExpect(jsonPath("$.data.item.replies[3].id").value(target.getId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void liveReplyUnderDeletedOrHiddenParentKeepsOnlyTheParentPlaceholder(boolean hidden) throws Exception {
        Comment target = comments.save(new Comment(article, parent, writer, "살아 있는 답글"));
        reactions.save(new CommentReaction(parent, viewer, CommentReactionType.LIKE));
        if (hidden) parent.hideByAdmin(); else parent.delete();
        comments.flush();

        mvc.perform(get(contextUrl(article, target.getId())).with(user(MemberPrincipal.ofSocial(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.deleted").value(true))
                .andExpect(jsonPath("$.data.item.hiddenByAdmin").value(hidden))
                .andExpect(jsonPath("$.data.item.content").value(nullValue()))
                .andExpect(jsonPath("$.data.item.nickname").value(nullValue()))
                .andExpect(jsonPath("$.data.item.likeCount").value(0))
                .andExpect(jsonPath("$.data.item.likedByMe").value(false))
                .andExpect(jsonPath("$.data.item.mine").value(false))
                .andExpect(jsonPath("$.data.item.replies[0].content").value("살아 있는 답글"));
        assertUnavailable(article, parent.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "hidden", "missing"})
    void unavailableTargetNeverReturnsItsOriginalText(String state) throws Exception {
        Comment target = comments.save(new Comment(article, parent, writer, "비공개 원문"));
        Long targetId = target.getId();
        if (state.equals("deleted")) target.delete();
        if (state.equals("hidden")) target.hideByAdmin();
        if (state.equals("missing")) targetId = Long.MAX_VALUE;
        assertUnavailable(article, targetId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"private", "draft", "scheduled", "futurePublished", "trashed", "purged"})
    void aNonPublicPostDoesNotExposeItsComment(String state) throws Exception {
        switch (state) {
            case "private" -> article.unpublish();
            case "draft" -> { article.schedule(LocalDateTime.now().plusDays(1)); article.cancelSchedule(); }
            case "scheduled" -> article.schedule(LocalDateTime.now().plusDays(1));
            case "futurePublished" -> article.publish(LocalDateTime.now().plusDays(1));
            case "trashed" -> article.delete(LocalDateTime.now());
            case "purged" -> { article.delete(LocalDateTime.now().minusDays(31)); article.purgeContent(LocalDateTime.now()); }
        }
        assertUnavailable(article, parent.getId());
    }

    @Test
    void anotherPostIdOrAnInconsistentParentCannotExposeAThread() throws Exception {
        Post other = publishedPost("다른 글", article.getCategory());
        assertUnavailable(other, parent.getId());
        Comment wrongParent = comments.save(new Comment(other, null, writer, "다른 글의 부모"));
        Comment inconsistentReply = comments.save(new Comment(article, wrongParent, writer, "잘못 연결된 답글"));
        assertUnavailable(article, inconsistentReply.getId());
    }

    private void assertUnavailable(Post post, Long commentId) throws Exception {
        comments.flush();
        mvc.perform(get(contextUrl(post, commentId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private Post publishedPost(String title, Category category) {
        Post post = new Post(title, "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now().minusMinutes(1));
        return posts.save(post);
    }

    private String contextUrl(Post post, Long commentId) {
        return "/api/v1/blog/posts/" + post.getId() + "/comments/" + commentId + "/context";
    }
}
