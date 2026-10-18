package me.jsjlog.blog.notification.repository;

import java.util.List;
import java.util.Optional;

import me.jsjlog.blog.notification.domain.Notification;
import me.jsjlog.blog.notification.domain.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 알림이 가리키는 것. 댓글({@code c}, 그 글 {@code p}) 아니면 방명록 글({@code g}) 하나다.
     *
     * <p>둘 다 바깥 조인이어야 한다. {@code n.comment.post} 처럼 경로로 쓰면 안 조인으로 바뀌어
     * 다른 쪽 알림이 통째로 빠진다.</p>
     */
    String TARGETS = """
             left join n.comment c
             left join c.post p
             left join n.guestbookEntry g
            """;

    // 일반 회원의 알림은 공개 글만 가리킨다. 신고 업무 알림은 관리자에게만
    // 비공개 글도 허용하며, 목록·개수·읽음 처리가 동일한 범위를 사용한다.
    //
    // 방명록 새 글·신고 알림은 운영자에게만 보인다. 지워진 글의 새 글 알림은 빼고, 신고 알림은 쓴 사람이
    // 지운 뒤에도 남긴다 — 지워도 신고 처리는 운영자가 해야 할 일이다. 원문을 파기했으면 볼 것이 없다.
    // 답글 알림은 그 글을 쓴 사람에게만 보이고, 글이 안 보이게 됐거나 답글을 지웠으면 빠진다.
    // 가림 알림도 쓴 사람에게만 보이고, 다시 공개되면 빠진다.
    String VISIBLE_TO_RECIPIENT = """
             and ((c.id is not null
                   and p.deletedAt is null
                   and (p.status = me.jsjlog.blog.post.domain.PostStatus.PUBLISHED
                        or (n.type = me.jsjlog.blog.notification.domain.NotificationType.REPORT_RECEIVED
                            and n.recipient.role = me.jsjlog.blog.member.domain.MemberRole.ADMIN))
                   and (c.deleted = false
                        or n.type = me.jsjlog.blog.notification.domain.NotificationType.COMMENT_HIDDEN))
               or (g.id is not null
                   and g.contentPurgedAt is null
                   and ((n.type in (me.jsjlog.blog.notification.domain.NotificationType.GUESTBOOK_WRITTEN,
                                    me.jsjlog.blog.notification.domain.NotificationType.GUESTBOOK_REPORTED)
                         and n.recipient.role = me.jsjlog.blog.member.domain.MemberRole.ADMIN
                         and (g.deleted = false
                              or n.type = me.jsjlog.blog.notification.domain.NotificationType.GUESTBOOK_REPORTED))
                     or (n.type = me.jsjlog.blog.notification.domain.NotificationType.GUESTBOOK_REPLIED
                         and g.member.id = n.recipient.id
                         and g.deleted = false
                         and g.replyContent is not null)
                     or (n.type = me.jsjlog.blog.notification.domain.NotificationType.GUESTBOOK_HIDDEN
                         and g.member.id = n.recipient.id
                         and g.deleted = true
                         and g.hiddenByAdmin = true))))
            """;

    /**
     * 내 알림 목록.
     *
     * <p>삭제된 댓글에서 온 알림은 빼고 준다. 눌러도 "삭제된 댓글입니다" 밖에 볼 게 없는데
     * 종에 숫자만 남으면 확인할 방법이 없는 알림이 쌓인다.</p>
     *
     * <p><b>{@code COMMENT_HIDDEN} 만 예외다.</b> 가려진 댓글은 {@code deleted = true} 로
     * 표시되는데, 그 알림이 알리려는 내용이 바로 "가려졌다" 는 사실이다. 같이 걸러 버리면
     * 만들자마자 사라져서 받는 사람은 영원히 못 본다.</p>
     *
     * <p>{@code join fetch} 로 댓글과 글을 같이 가져온다. 목록마다 누가·어느 글에 썼는지가
     * 필요해서 LAZY 로 두면 알림 수만큼 조회가 더 나간다.</p>
     */
    @Query("""
            select n from Notification n
            left join fetch n.comment c
            left join fetch c.post p
            left join fetch c.member
            left join fetch n.guestbookEntry g
            left join fetch g.member
            left join fetch g.replyMember
            where n.recipient.id = :memberId
            """ + VISIBLE_TO_RECIPIENT + " order by n.id desc")
    List<Notification> findMine(@Param("memberId") Long memberId, Pageable pageable);

    /** 종에 붙는 숫자. 목록과 같은 조건이어야 눌렀을 때 개수가 맞는다 */
    @Query("select count(n) from Notification n" + TARGETS + """
            where n.recipient.id = :memberId
              and n.readAt is null
            """ + VISIBLE_TO_RECIPIENT)
    long countUnread(@Param("memberId") Long memberId);

    @Query("select n from Notification n" + TARGETS + " where n.id = :id and n.recipient.id = :recipientId"
            + VISIBLE_TO_RECIPIENT)
    Optional<Notification> findByIdAndRecipientId(@Param("id") Long id, @Param("recipientId") Long recipientId);

    @Query("select n from Notification n" + TARGETS + " where n.recipient.id = :memberId and n.readAt is null"
            + VISIBLE_TO_RECIPIENT)
    List<Notification> findUnread(@Param("memberId") Long memberId);

    /**
     * 같은 일로 이미 울린 적이 있는지.
     *
     * <p>신고가 다섯 건이면 알림도 다섯 개가 되는 것을 막는 자리다. 종이 폭주하면 그 폭주
     * 자체가 공격 수단이 된다 — 자동 숨김을 뺀 것과 같은 이유다. 이미 있는 알림을 눌러
     * 들어가면 신고 전체 건수와 사유를 보게 되므로 잃는 정보는 없다.</p>
     *
     * <p><b>받는 사람별로 본다.</b> 운영자가 둘일 때 한 명이 읽었다고 다른 한 명이 못 받으면
     * 안 된다.</p>
     */
    boolean existsByRecipientIdAndTypeAndCommentIdAndReadAtIsNull(
            Long recipientId,
            NotificationType type,
            Long commentId
    );

    /** 방명록 신고 알림의 같은 검사. 이유는 위와 같다 */
    boolean existsByRecipientIdAndTypeAndGuestbookEntryIdAndReadAtIsNull(
            Long recipientId,
            NotificationType type,
            Long guestbookEntryId
    );
}
