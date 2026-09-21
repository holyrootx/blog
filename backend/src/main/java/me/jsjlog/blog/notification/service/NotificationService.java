package me.jsjlog.blog.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.Member;
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
