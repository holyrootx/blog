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
            join fetch n.comment c
            join fetch c.post
            join fetch c.member
            where n.recipient.id = :memberId
              and (c.deleted = false or n.type = me.jsjlog.blog.notification.domain.NotificationType.COMMENT_HIDDEN)
            order by n.id desc
            """)
    List<Notification> findMine(@Param("memberId") Long memberId, Pageable pageable);

    /** 종에 붙는 숫자. 목록과 같은 조건이어야 눌렀을 때 개수가 맞는다 */
    @Query("""
            select count(n) from Notification n
            join n.comment c
            where n.recipient.id = :memberId
              and n.readAt is null
              and (c.deleted = false or n.type = me.jsjlog.blog.notification.domain.NotificationType.COMMENT_HIDDEN)
            """)
    long countUnread(@Param("memberId") Long memberId);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    @Query("select n from Notification n where n.recipient.id = :memberId and n.readAt is null")
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
}
