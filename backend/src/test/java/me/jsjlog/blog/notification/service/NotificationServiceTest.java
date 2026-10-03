package me.jsjlog.blog.notification.service;

import java.time.LocalDateTime;
import java.util.List;

import me.jsjlog.blog.admin.dto.AdminCommentVisibilityRequest;
import me.jsjlog.blog.admin.service.AdminCommentService;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.notification.domain.NotificationType;
import me.jsjlog.blog.notification.dto.NotificationResponse;
import me.jsjlog.blog.notification.repository.NotificationRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.CommentReportReason;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.dto.CommentCreateRequest;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentReportRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.post.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 알림이 누구에게 가고 누구에게 안 가는지.
 *
 * <p>로그인해서 댓글을 쓴 사람이 얻는 것을 만드는 기능이라, 안 가야 할 곳에 가는 것보다
 * 가야 할 곳에 안 가는 쪽이 더 나쁘다. 양쪽 다 확인한다.</p>
 */
@SpringBootTest
@Transactional
class NotificationServiceTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AdminCommentService adminCommentService;

    @Autowired
    private CommentReportRepository commentReportRepository;

    private Member author;
    private Member reader;
    private Member stranger;
    private Post post;

    @BeforeEach
    void seed() {
        author = memberRepository.save(social("author", "글쓴이"));
        reader = memberRepository.save(social("reader", "읽는사람"));
        stranger = memberRepository.save(social("stranger", "지나가던사람"));

        Category category = categoryRepository.save(new Category("알림테스트", 900L));

        post = new Post("알림 확인용 글", "요약", "본문", category, null, author);
        post.publish(LocalDateTime.now());
        post = postRepository.save(post);
    }

    private static Member social(String providerUserId, String nickname) {
        return Member.ofSocial(AuthProvider.GOOGLE, providerUserId, nickname, null, null);
    }

    /** 운영자는 따로 만든다. 신고 알림은 role 로 받는 사람을 찾는다 */
    private Member admin(String username, String nickname) {
        return memberRepository.save(Member.ofLocalAdmin(username, "encoded", nickname));
    }

    private Long comment(String content, Long parentId, Member writer) {
        return commentService
                .createComment(post.getId(), new CommentCreateRequest(content, parentId), writer.getId())
                .id();
    }

    @Test
    @DisplayName("내 글에 남이 댓글을 달면 글쓴이에게 알림이 간다")
    void notifiesPostAuthor() {
        comment("잘 봤습니다", null, reader);

        List<NotificationResponse> mine = notificationService.findMine(author.getId());

        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).type()).isEqualTo(NotificationType.POST_COMMENT);
        assertThat(mine.get(0).actorNickname()).isEqualTo("읽는사람");
        assertThat(mine.get(0).postId()).isEqualTo(post.getId());
    }

    @Test
    @DisplayName("내 댓글에 답글이 달리면 나에게 알림이 간다")
    void notifiesParentCommentWriter() {
        Long parent = comment("첫 댓글", null, reader);
        comment("답글입니다", parent, stranger);

        List<NotificationResponse> mine = notificationService.findMine(reader.getId());

        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).type()).isEqualTo(NotificationType.REPLY);
        assertThat(mine.get(0).actorNickname()).isEqualTo("지나가던사람");
    }

    @Test
    @DisplayName("내 글에 내가 댓글을 달면 나에게는 안 온다")
    void doesNotNotifySelfOnOwnPost() {
        comment("자문자답", null, author);

        assertThat(notificationService.findMine(author.getId())).isEmpty();
        assertThat(notificationService.countUnread(author.getId())).isZero();
    }

    @Test
    @DisplayName("내 댓글에 내가 답글을 달면 나에게는 안 온다")
    void doesNotNotifySelfOnOwnComment() {
        Long parent = comment("내 댓글", null, reader);
        comment("내가 다는 답글", parent, reader);

        assertThat(notificationService.findMine(reader.getId())).isEmpty();
    }

    @Test
    @DisplayName("답글일 때는 글쓴이에게 따로 가지 않는다 — 한 댓글로 알림이 둘이 되지 않게")
    void replyDoesNotAlsoNotifyAuthor() {
        Long parent = comment("첫 댓글", null, reader);

        // 글쓴이는 최상위 댓글 알림 하나만 받은 상태다
        assertThat(notificationService.countUnread(author.getId())).isEqualTo(1);

        comment("답글입니다", parent, stranger);

        assertThat(notificationService.countUnread(author.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("답글이 숨겨지면 알림 목록과 개수에서 빠진다")
    void hiddenCommentDisappearsFromNotifications() {
        Long parent = comment("첫 댓글", null, reader);
        Long reply = comment("곧 숨겨질 답글", parent, stranger);

        assertThat(notificationService.countUnread(reader.getId())).isEqualTo(1);

        commentRepository.findById(reply).orElseThrow().delete();
        commentRepository.flush();

        // 눌러도 볼 것이 없는 알림이라 목록에서도 숫자에서도 뺀다
        assertThat(notificationService.findMine(reader.getId())).isEmpty();
        assertThat(notificationService.countUnread(reader.getId())).isZero();
    }

    @Test
    @DisplayName("읽으면 안 읽은 개수가 줄어든다")
    void markReadReducesUnreadCount() {
        comment("잘 봤습니다", null, reader);

        Long notificationId = notificationService.findMine(author.getId()).get(0).id();

        notificationService.markRead(notificationId, author.getId());

        assertThat(notificationService.countUnread(author.getId())).isZero();
        assertThat(notificationService.findMine(author.getId()).get(0).unread()).isFalse();
    }

    @Test
    @DisplayName("남의 알림은 읽음 처리할 수 없다")
    void cannotMarkOthersNotification() {
        comment("잘 봤습니다", null, reader);

        Long notificationId = notificationService.findMine(author.getId()).get(0).id();

        assertThatThrownBy(() -> notificationService.markRead(notificationId, stranger.getId()))
                .isInstanceOf(BlogException.class);

        assertThat(notificationService.countUnread(author.getId())).isEqualTo(1);
    }

    // ── 신고 알림 ──────────────────────────────

    @Test
    @DisplayName("신고가 들어오면 운영자에게 알림이 간다")
    void notifiesAdminOnReport() {
        Member admin = admin("report-admin-1", "운영자");
        Long target = comment("문제가 된 댓글", null, reader);

        commentService.reportComment(target, CommentReportReason.ABUSE, null, stranger.getId());

        List<NotificationResponse> mine = notificationService.findMine(admin.getId());

        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).type()).isEqualTo(NotificationType.REPORT_RECEIVED);
        // 신고자가 아니라 신고당한 댓글을 쓴 사람이다. 운영자에게 필요한 쪽이 이쪽이다
        assertThat(mine.get(0).actorNickname()).isEqualTo("읽는사람");
    }

    @Test
    @DisplayName("운영자가 여럿이면 전부 받는다")
    void notifiesEveryAdmin() {
        Member first = admin("report-admin-2", "운영자1");
        Member second = admin("report-admin-3", "운영자2");
        Long target = comment("문제가 된 댓글", null, reader);

        commentService.reportComment(target, CommentReportReason.SPAM, null, stranger.getId());

        assertThat(notificationService.countUnread(first.getId())).isEqualTo(1);
        assertThat(notificationService.countUnread(second.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("운영자가 쓴 댓글이 신고돼도 운영자에게 알림이 간다")
    void notifiesAdminEvenWhenAdminWroteTheComment() {
        Member admin = admin("report-admin-4", "운영자");
        Long target = commentRepository
                .save(new Comment(post, null, admin, "운영자가 쓴 댓글"))
                .getId();

        commentService.reportComment(target, CommentReportReason.ABUSE, null, stranger.getId());

        // 받는 사람과 댓글 쓴 사람이 같다. 답글 알림이라면 버려야 하지만 신고는 다르다 —
        // 여기서 버리면 운영자가 쓴 댓글은 아무리 신고돼도 아무도 모른다
        assertThat(notificationService.countUnread(admin.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("안 읽은 신고 알림이 있으면 신고가 더 들어와도 알림은 하나뿐이다")
    void doesNotPileUpReportNotifications() {
        Member admin = admin("report-admin-5", "운영자");
        Long target = comment("몰매 맞는 댓글", null, reader);

        commentService.reportComment(target, CommentReportReason.ABUSE, null, stranger.getId());
        commentService.reportComment(target, CommentReportReason.SPAM, null, author.getId());

        // 신고는 둘 다 접수된다. 종만 한 번 울린다 — 알림이 쌓이면 그 폭주가 공격 수단이 된다
        assertThat(commentReportRepository.count()).isEqualTo(2);
        assertThat(notificationService.countUnread(admin.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("운영자가 읽고 난 뒤 들어온 신고는 다시 알린다")
    void notifiesAgainAfterAdminReadsIt() {
        Member admin = admin("report-admin-6", "운영자");
        Long target = comment("몰매 맞는 댓글", null, reader);

        commentService.reportComment(target, CommentReportReason.ABUSE, null, stranger.getId());
        notificationService.markRead(
                notificationService.findMine(admin.getId()).get(0).id(), admin.getId());

        commentService.reportComment(target, CommentReportReason.SPAM, null, author.getId());

        assertThat(notificationService.countUnread(admin.getId())).isEqualTo(1);
        assertThat(notificationService.findMine(admin.getId())).hasSize(2);
    }

    // ── 가려짐 알림 ────────────────────────────

    @Test
    @DisplayName("댓글을 가리면 쓴 사람에게 알림이 간다 — 가려진 뒤에도 목록에 남는다")
    void notifiesWriterWhenHidden() {
        Member admin = admin("hide-admin-1", "운영자");
        Long target = comment("가려질 댓글", null, reader);

        adminCommentService.updateVisibility(
                target, new AdminCommentVisibilityRequest(true, "확인용"), admin.getId());

        List<NotificationResponse> mine = notificationService.findMine(reader.getId());

        // 가린 댓글은 deleted = true 가 된다. 목록 조회가 그걸 그대로 거르면 이 알림은
        // 만들어지자마자 사라져서 받는 사람은 영원히 못 본다 — 이 타입만 통과시킨다
        assertThat(mine)
                .extracting(NotificationResponse::type)
                .contains(NotificationType.COMMENT_HIDDEN);
        assertThat(notificationService.countUnread(reader.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("가린 것을 되돌릴 때는 알리지 않는다")
    void doesNotNotifyOnRestore() {
        Member admin = admin("hide-admin-2", "운영자");
        Long target = comment("가렸다 되돌릴 댓글", null, reader);

        adminCommentService.updateVisibility(
                target, new AdminCommentVisibilityRequest(true, null), admin.getId());
        adminCommentService.updateVisibility(
                target, new AdminCommentVisibilityRequest(false, null), admin.getId());

        // 가려짐 알림 하나뿐이다. 되돌린 것은 받는 사람이 할 일이 없다
        assertThat(notificationService.findMine(reader.getId()))
                .extracting(NotificationResponse::type)
                .containsExactly(NotificationType.COMMENT_HIDDEN);
    }

    @Test
    @DisplayName("글쓴이가 없는 옛 글에 댓글이 달려도 터지지 않는다")
    void handlesPostWithoutAuthor() {
        Category category = categoryRepository.save(new Category("옛글", 901L));
        Post legacy = new Post("author 컬럼 이전 글", "요약", "본문", category, null, null);
        legacy.publish(LocalDateTime.now());
        legacy = postRepository.save(legacy);

        Comment written = commentRepository.save(new Comment(legacy, null, reader, "댓글"));

        notificationService.notifyCommentCreated(written);

        assertThat(notificationRepository.count()).isZero();
    }
}
