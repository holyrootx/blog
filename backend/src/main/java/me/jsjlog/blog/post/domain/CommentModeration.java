package me.jsjlog.blog.post.domain;

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
import me.jsjlog.blog.member.domain.Member;

import java.time.LocalDateTime;

/**
 * 운영자가 댓글에 한 조치의 기록.
 *
 * <p>댓글에는 지금 상태만 남는다. 가려져 있다는 것은 알 수 있어도 언제 누가 왜 가렸는지는
 * 어디에도 없어서, 글쓴이가 "왜 사라졌냐" 물으면 가리킬 것이 없다.</p>
 *
 * <p>되돌린 것도 남긴다. 가렸다가 되돌린 일은 판단이 바뀐 것이고, 그 사실이 사라지면
 * 나중에 보는 사람은 처음부터 아무 일도 없었던 것으로 읽는다.</p>
 *
 * <p>쌓기만 하고 고치지 않으므로 {@code BaseEntity} 를 물려받지 않는다 —
 * 수정자·수정시각 칸이 영원히 비어 있게 된다.</p>
 */
@Getter
@Entity
@Table(
        name = "comment_moderation",
        indexes = {
                @Index(name = "idx_comment_moderation_comment", columnList = "comment_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentModeration {

    /** 사유 길이. 운영자가 자기가 보려고 적는 메모라 길 필요가 없다 */
    public static final int MAX_REASON_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    /** 조치한 운영자 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Member admin;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private CommentModerationAction action;

    @Column(name = "reason", length = MAX_REASON_LENGTH)
    private String reason;

    @Column(name = "acted_at", nullable = false)
    private LocalDateTime actedAt;

    private CommentModeration(
            Comment comment,
            Member admin,
            CommentModerationAction action,
            String reason
    ) {
        this.comment = comment;
        this.admin = admin;
        this.action = action;
        this.reason = trim(reason);
        this.actedAt = LocalDateTime.now();
    }

    public static CommentModeration of(
            Comment comment,
            Member admin,
            CommentModerationAction action,
            String reason
    ) {
        return new CommentModeration(comment, admin, action, reason);
    }

    private static String trim(String reason) {
        if (reason == null) {
            return null;
        }

        String text = reason.trim();

        if (text.isEmpty()) {
            return null;
        }

        return text.length() <= MAX_REASON_LENGTH ? text : text.substring(0, MAX_REASON_LENGTH);
    }
}
