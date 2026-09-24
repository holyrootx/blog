package me.jsjlog.blog.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.notification.domain.Notification;
import me.jsjlog.blog.notification.domain.NotificationType;
import me.jsjlog.blog.notification.dto.NotificationResponse;
import me.jsjlog.blog.notification.repository.NotificationRepository;
import me.jsjlog.blog.post.domain.Comment;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림을 남기고 읽는다.
 *
 * <p>실시간으로 밀어 주지 않는다. 앱이 뜰 때와 화면을 옮길 때 개수를 한 번씩 받아 가면 된다 —
 * 답글은 몇 시간 늦게 봐도 아무 문제가 없고, SSE·웹소켓은 이 규모에서 커넥션만 잡아먹는다.</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 종을 열었을 때 한 번에 보여 줄 개수. 더 필요해지면 그때 페이지를 붙인다 */
    private static final int LIST_LIMIT = 30;

    private final NotificationRepository notificationRepository;

    /** 신고 알림을 받을 운영자를 찾는 용도다. 다른 회원 조회는 여기서 하지 않는다 */
    private final MemberRepository memberRepository;

    /**
     * 댓글이 하나 달렸을 때 알릴 사람을 고른다.
     *
     * <p>답글이면 <b>부모 댓글을 쓴 사람</b>에게 간다. 최상위 댓글이면 <b>글쓴이</b>에게 간다.</p>
     *
     * <p>답글일 때 글쓴이에게도 같이 보내지 않는다. 한 번 달린 댓글로 알림이 두 개 생기고,
     * 부모 댓글을 쓴 사람이 글쓴이 본인이면 같은 일로 두 번 울린다. 글쓴이가 대화 전체를
     * 봐야 하는 경우는 관리자 댓글 화면이 맡는다.</p>
     */
    @Transactional
    public void notifyCommentCreated(Comment comment) {
        if (comment.isReply()) {
            notify(comment.getParent().getMember(), NotificationType.REPLY, comment);
            return;
        }

        notify(comment.getPost().getAuthor(), NotificationType.POST_COMMENT, comment);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> findMine(Long memberId) {
        return notificationRepository
                .findMine(memberId, PageRequest.of(0, LIST_LIMIT))
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(Long memberId) {
        return notificationRepository.countUnread(memberId);
    }

    @Transactional
    public void markRead(Long notificationId, Long memberId) {
        notificationRepository
                .findByIdAndRecipientId(notificationId, memberId)
                // 남의 알림 번호를 넣어도 여기서 걸린다. 찾을 수 없다고만 답한다 —
                // "있는데 네 것이 아니다" 라고 알려 주면 남의 알림 존재를 확인해 줄 수 있다
                .orElseThrow(() -> new BlogException(ErrorCode.NOTIFICATION_NOT_FOUND))
                .markRead(LocalDateTime.now());
    }

    @Transactional
    public void markAllRead(Long memberId) {
        LocalDateTime now = LocalDateTime.now();

        notificationRepository.findUnread(memberId).forEach(notification -> notification.markRead(now));
    }

    /**
     * 신고가 들어왔다고 운영자에게 알린다.
     *
     * <p><b>{@link #notify} 를 쓰지 않는다.</b> 거기에는 "받는 사람이 댓글 쓴 사람이면 버린다"
     * 는 검사가 있는데, 답글·글댓글에는 맞지만 신고에는 틀린다. 관리자도 댓글을 쓰고
     * 신고는 남의 댓글이면 누구나 할 수 있어서, 관리자가 쓴 댓글이 신고당하면
     * 받는 사람(운영자) 과 댓글 쓴 사람이 같아져 알림이 조용히 사라진다.</p>
     *
     * <p>신고 알림이 뜻하는 것은 "누가 누구에게 무엇을 했나" 가 아니라 <b>"운영자가 봐야 할
     * 일이 생겼다"</b> 이다. 그래서 받는 사람과 댓글 쓴 사람의 관계를 따지지 않는다.</p>
     */
    @Transactional
    public void notifyReportReceived(Comment comment) {
        for (Member admin : memberRepository.findAllByRole(MemberRole.ADMIN)) {
            // 이미 울려 놓고 아직 안 본 건이 있으면 더 울리지 않는다. 신고 다섯 건에
            // 알림 다섯 개가 되면 그 폭주가 그대로 공격 수단이 된다
            boolean alreadyWaiting = notificationRepository
                    .existsByRecipientIdAndTypeAndCommentIdAndReadAtIsNull(
                            admin.getId(), NotificationType.REPORT_RECEIVED, comment.getId());

            if (alreadyWaiting) {
                continue;
            }

            notificationRepository.save(
                    new Notification(admin, NotificationType.REPORT_RECEIVED, comment));
        }
    }

    /**
     * 댓글을 가렸다고 쓴 사람에게 알린다.
     *
     * <p>사유는 싣지 않는다. 화면 문구도 "운영 기준에 따라" 까지만 말한다 — 사유를 붙이면
     * 어떤 댓글에 누가 왜 신고했는지가 좁혀지고, 댓글이 몇 개 없는 블로그에서는 거의
     * 특정된다.</p>
     *
     * <p>되돌릴 때({@code restore}) 는 보내지 않는다. 받는 사람이 할 일이 없다.</p>
     *
     * <p>같은 댓글을 되돌렸다가 다시 가리면 그때는 다시 보낸다. 보이던 것이 또 가려진
     * 것이라 새로 알릴 일이 맞다.</p>
     */
    @Transactional
    public void notifyCommentHidden(Comment comment) {
        Member author = comment.getMember();

        if (author == null) {
            return;
        }

        notificationRepository.save(
                new Notification(author, NotificationType.COMMENT_HIDDEN, comment));
    }

    private void notify(Member recipient, NotificationType type, Comment comment) {
        // 글쓴이가 없는 글이 있다. author 컬럼이 생기기 전에 쓴 글이다
        if (recipient == null) {
            return;
        }

        // 내 댓글에 내가 답글을 달거나 내 글에 내가 댓글을 다는 경우다.
        // 방금 내가 한 일을 알림으로 돌려받을 이유가 없다
        if (Objects.equals(recipient.getId(), comment.getMember().getId())) {
            return;
        }

        notificationRepository.save(new Notification(recipient, type, comment));
    }
}
