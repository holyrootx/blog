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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.jsjlog.blog.common.domain.BaseEntity;
import me.jsjlog.blog.member.domain.Member;

import java.time.LocalDateTime;

/**
 * 댓글 신고.
 *
 * <p>신고가 쌓여도 댓글이 저절로 숨지 않는다. 여럿이 몰려 신고하면 멀쩡한 글이 사라지고,
 * 그건 신고 기능이 도리어 공격 수단이 되는 것이다. 숨길지는 사람이 보고 정한다 —
 * 관리자 댓글 화면에 이미 숨기기가 있다.</p>
 *
 * <p>한 사람이 같은 댓글을 여러 번 신고하지 못하게 막는다. 안 막으면 혼자서 숫자를
 * 부풀릴 수 있어서, 받는 쪽에서 신고 수를 믿을 수 없게 된다.</p>
 */
@Getter
@Entity
@Table(
        name = "comment_report",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_comment_report_comment_member",
                columnNames = {"comment_id", "member_id"}
        ),
        indexes = {
                @Index(name = "idx_comment_report_comment", columnList = "comment_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentReport extends BaseEntity {

    /** 기타 사유에 덧붙이는 설명의 길이. 길게 받을 자리가 아니다 — 자세한 사연은 메일로 온다 */
    public static final int MAX_DETAIL_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    /** 신고한 사람. 누가 신고했는지는 관리자 화면에 내보내지 않는다 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private CommentReportReason reason;

    @Column(name = "detail", length = MAX_DETAIL_LENGTH)
    private String detail;

    /**
     * 운영자가 이 신고를 보고 판단을 내린 시각. 아직이면 비어 있다.
     *
     * <p>이게 없으면 신고 수만 남아서, 이미 본 신고와 새로 온 신고를 가릴 수 없다.
     * 처리해도 숫자가 그대로라 목록에서 계속 눈에 걸린다.</p>
     */
    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    public CommentReport(Comment comment, Member member, CommentReportReason reason, String detail) {
        this.comment = comment;
        this.member = member;
        this.reason = reason;
        this.detail = trim(detail);
    }

    /** 운영자가 판단을 내렸다. 이미 처리한 신고는 다시 건드리지 않는다 */
    public void markHandled() {
        if (handledAt == null) {
            this.handledAt = LocalDateTime.now();
        }
    }

    private static String trim(String detail) {
        if (detail == null) {
            return null;
        }

        String text = detail.trim();

        if (text.isEmpty()) {
            return null;
        }

        return text.length() <= MAX_DETAIL_LENGTH ? text : text.substring(0, MAX_DETAIL_LENGTH);
    }
}
