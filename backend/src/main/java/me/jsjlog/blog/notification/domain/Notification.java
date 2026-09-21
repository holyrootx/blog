package me.jsjlog.blog.notification.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.jsjlog.blog.common.domain.BaseEntity;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.post.domain.Comment;

/**
 * 회원에게 생긴 일.
 *
 * <p>로그인해서 댓글을 쓴 사람이 얻는 것을 만드는 장치다. 답글이 달려도 본인이 모르면
 * 자기가 쓴 글을 기억해서 다시 찾아와야 하고, 그러면 한 번 쓰고 다시 오지 않는다.</p>
 *
 * <p><b>문구를 저장하지 않는다.</b> 댓글을 가리키고 화면에서 조인해 읽는다. 문구를 박아 두면
 * 댓글이 수정됐을 때 알림에 남은 글이 실제와 달라진다. 이 규모에서 비정규화는 이르다.</p>
 *
 * <p>이메일은 알림 통로가 될 수 없다. 최소수집 원칙으로 널을 허용하고 unique 도 걸지 않아서
 * 없는 회원이 있다. 통로는 화면의 종 아이콘 하나뿐이다.</p>
 */
@Getter
@Entity
@Table(
        name = "notification",
        // 종을 열 때마다 "내 것 중 안 읽은 것" 을 센다. 두 컬럼을 같이 타는 조회라 같이 묶는다
        indexes = @Index(name = "ix_notification_recipient", columnList = "recipient_id, read_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 받는 사람 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Member recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    /**
     * 이 알림을 만든 댓글. 답글이면 답글 자신이다.
     *
     * 화면은 이걸 따라가서 누가 무슨 말을 했는지, 어느 글인지 읽는다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    /** 읽은 시각. 널이면 안 읽은 것이다 */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    public Notification(Member recipient, NotificationType type, Comment comment) {
        this.recipient = recipient;
        this.type = type;
        this.comment = comment;
    }

    /** 이미 읽은 알림을 다시 읽어도 처음 읽은 시각을 유지한다 */
    public void markRead(LocalDateTime readAt) {
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }

    public boolean isUnread() {
        return readAt == null;
    }
}
