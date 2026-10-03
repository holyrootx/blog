package me.jsjlog.blog.admin.service;

import java.time.LocalDateTime;
import me.jsjlog.blog.admin.dto.AdminCommentReplyRequest;
import me.jsjlog.blog.history.repository.ContentHistoryRepository;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.notification.domain.Notification;
import me.jsjlog.blog.notification.repository.NotificationRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:reply_transaction;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class AdminReplyTransactionTest {
    @Autowired AdminCommentService adminComments;
    @Autowired MemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired PostRepository posts;
    @Autowired CommentRepository comments;
    @Autowired ContentHistoryRepository histories;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean NotificationRepository notifications;

    @Test
    void notificationFailureRollsBackReplyAndHistoryTogether() {
        long[] ids = new TransactionTemplate(transactions).execute(status -> {
            Member admin = members.save(Member.ofLocalAdmin("reply-transaction-admin", "encoded", "관리자"));
            Member reader = members.save(Member.ofSocial(AuthProvider.GOOGLE, "reply-transaction-reader", "독자", null, null));
            Category category = categories.save(new Category("답글 원자성", 882L));
            Post post = posts.save(new Post("질문 글", "요약", "본문", category, null, admin));
            post.publish(LocalDateTime.now());
            Comment parent = comments.save(new Comment(post, null, reader, "질문"));
            return new long[]{parent.getId(), admin.getId()};
        });
        doThrow(new IllegalStateException("notification unavailable")).when(notifications).save(any(Notification.class));
        assertThatThrownBy(() -> adminComments.reply(ids[0], new AdminCommentReplyRequest("답변"), ids[1]))
                .isInstanceOf(IllegalStateException.class);
        assertThat(comments.count()).isEqualTo(1);
        assertThat(histories.count()).isZero();
        assertThat(notifications.count()).isZero();
    }
}
