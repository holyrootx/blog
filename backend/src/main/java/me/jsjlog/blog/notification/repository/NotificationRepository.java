package me.jsjlog.blog.notification.repository;

import java.util.List;
import java.util.Optional;

import me.jsjlog.blog.notification.domain.Notification;
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
     * <p>{@code join fetch} 로 댓글과 글을 같이 가져온다. 목록마다 누가·어느 글에 썼는지가
     * 필요해서 LAZY 로 두면 알림 수만큼 조회가 더 나간다.</p>
     */
    @Query("""
            select n from Notification n
            join fetch n.comment c
            join fetch c.post
            join fetch c.member
            where n.recipient.id = :memberId and c.deleted = false
            order by n.id desc
            """)
    List<Notification> findMine(@Param("memberId") Long memberId, Pageable pageable);

    /** 종에 붙는 숫자. 목록과 같은 조건이어야 눌렀을 때 개수가 맞는다 */
    @Query("""
            select count(n) from Notification n
            join n.comment c
            where n.recipient.id = :memberId and n.readAt is null and c.deleted = false
            """)
    long countUnread(@Param("memberId") Long memberId);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    @Query("select n from Notification n where n.recipient.id = :memberId and n.readAt is null")
    List<Notification> findUnread(@Param("memberId") Long memberId);
}
