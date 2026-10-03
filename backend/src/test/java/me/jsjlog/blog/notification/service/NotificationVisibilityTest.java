package me.jsjlog.blog.notification.service;

import java.time.LocalDateTime;
import me.jsjlog.blog.admin.dto.AdminCommentReplyRequest;
import me.jsjlog.blog.admin.service.AdminCommentService;
import me.jsjlog.blog.admin.service.AdminPostService;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.notification.domain.Notification;
import me.jsjlog.blog.notification.domain.NotificationType;
import me.jsjlog.blog.notification.dto.NotificationResponse;
import me.jsjlog.blog.notification.repository.NotificationRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.domain.PostStatus;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationVisibilityTest {
    @Autowired MemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired PostRepository posts;
    @Autowired CommentRepository comments;
    @Autowired NotificationRepository notificationRepository;
    @Autowired AdminCommentService adminComments;
    @Autowired AdminPostService adminPosts;
    @Autowired NotificationService notifications;
    @Autowired MockMvc mvc;
    Member admin;
    Member reader;
    Post post;
    Comment parent;

    @BeforeEach
    void seed() {
        admin = members.save(Member.ofLocalAdmin("visibility-admin", "encoded", "관리자"));
        reader = members.save(Member.ofSocial(AuthProvider.GOOGLE, "visibility-reader", "독자", null, null));
        Category category = categories.save(new Category("공개 범위 회귀", 881L));
        post = posts.save(new Post("공개 제목", "요약", "본문", category, null, admin));
        post.publish(LocalDateTime.now());
        parent = comments.save(new Comment(post, null, reader, "독자 질문"));
    }

    @Test
    void adminReplyCreatesExactlyOneNotificationForParentAuthor() {
        Long replyId = adminComments.reply(parent.getId(), new AdminCommentReplyRequest("관리자 답변"), admin.getId());
        assertThat(comments.findById(replyId)).isPresent();
        assertThat(notifications.findMine(reader.getId()))
                .extracting(NotificationResponse::type).containsExactly(NotificationType.REPLY);
        assertThat(notifications.countUnread(reader.getId())).isEqualTo(1);
        assertThat(notifications.findMine(admin.getId())).isEmpty();
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void adminReplyToOwnCommentDoesNotNotifySelf() {
        Comment own = comments.save(new Comment(post, null, admin, "관리자 댓글"));
        adminComments.reply(own.getId(), new AdminCommentReplyRequest("자기 답글"), admin.getId());
        assertThat(notificationRepository.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = {"PRIVATE", "DRAFT", "SCHEDULED"})
    void inaccessiblePostsDoNotExposeChangedTitleOrLinksInMemberApis(PostStatus state) throws Exception {
        adminComments.reply(parent.getId(), new AdminCommentReplyRequest("답글"), admin.getId());
        assertThat(notifications.countUnread(reader.getId())).isEqualTo(1);
        makeInaccessible(state);
        post.update("비공개로 바꾼 새 제목", "새 요약", "새 본문", post.getCategory(), null);
        posts.flush();
        assertHiddenFromReader();
    }

    @Test
    void deletedAndDraftRestoredPostsStayHiddenUntilRepublishedWithoutDeletingRelations() throws Exception {
        adminComments.reply(parent.getId(), new AdminCommentReplyRequest("답글"), admin.getId());
        adminPosts.deletePost(post.getId());
        assertHiddenFromReader();
        adminPosts.restorePost(post.getId());
        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertHiddenFromReader();
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(comments.count()).isEqualTo(2);
        adminPosts.publishPost(post.getId(), null);
        assertThat(notifications.findMine(reader.getId())).hasSize(1);
        assertThat(notifications.countUnread(reader.getId())).isEqualTo(1);
        assertThat(comments.findMyComments(reader.getId(), PageRequest.of(0, 20))).hasSize(1);
    }

    @Test
    void hiddenNotificationsCannotBeMarkedReadAndBulkReadOnlyTouchesVisibleItems() {
        adminComments.reply(parent.getId(), new AdminCommentReplyRequest("답글"), admin.getId());
        Long hiddenId = notifications.findMine(reader.getId()).getFirst().id();
        adminPosts.unpublishPost(post.getId());
        Post other = posts.save(new Post("다른 공개 글", "요약", "본문", post.getCategory(), null, admin));
        other.publish(LocalDateTime.now());
        Comment otherReply = comments.save(new Comment(other, null, admin, "공개 댓글"));
        Notification visible = notificationRepository.save(new Notification(reader, NotificationType.REPLY, otherReply));
        assertThatThrownBy(() -> notifications.markRead(hiddenId, reader.getId())).isInstanceOf(BlogException.class);
        notifications.markAllRead(reader.getId());
        assertThat(notificationRepository.findById(hiddenId).orElseThrow().getReadAt()).isNull();
        assertThat(visible.getReadAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = {"PRIVATE", "DRAFT", "SCHEDULED"})
    void adminReportWorkRemainsVisibleOnActiveNonPublicPosts(PostStatus state) {
        notifications.notifyReportReceived(parent);
        makeInaccessible(state);
        assertThat(notifications.findMine(admin.getId()))
                .extracting(NotificationResponse::type).containsExactly(NotificationType.REPORT_RECEIVED);
        assertThat(notifications.countUnread(admin.getId())).isEqualTo(1);
        notifications.markAllRead(admin.getId());
        assertThat(notifications.countUnread(admin.getId())).isZero();
        assertThat(notifications.findMine(admin.getId()).getFirst().unread()).isFalse();
    }

    @Test
    void reportTypeDoesNotBypassVisibilityForOrdinaryRecipient() {
        notificationRepository.save(new Notification(reader, NotificationType.REPORT_RECEIVED, parent));
        adminPosts.unpublishPost(post.getId());
        assertThat(notifications.findMine(reader.getId())).isEmpty();
        assertThat(notifications.countUnread(reader.getId())).isZero();
    }

    private void makeInaccessible(PostStatus state) {
        if (state == PostStatus.PRIVATE) {
            adminPosts.unpublishPost(post.getId());
        } else if (state == PostStatus.DRAFT) {
            adminPosts.deletePost(post.getId());
            adminPosts.restorePost(post.getId());
        } else {
            post.schedule(LocalDateTime.now().plusDays(1));
        }
    }

    private void assertHiddenFromReader() throws Exception {
        var authenticated = user(MemberPrincipal.ofSocial(reader));
        mvc.perform(get("/api/v1/blog/posts/" + post.getId()).with(authenticated))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/auth/me/comments").with(authenticated))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/v1/auth/me/notifications").with(authenticated))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/v1/auth/me/notifications/unread-count").with(authenticated))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(0));
    }
}
