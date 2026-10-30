package me.jsjlog.blog.guestbook.domain;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.jsjlog.blog.common.domain.BaseEntity;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.post.domain.CommentReportReason;

/**
 * 방명록 신고.
 *
 * <p>사유는 댓글 신고와 같은 목록을 쓴다. 신고 창도 같은 것을 쓰고, 운영자가 두 곳에서 다른
 * 말로 같은 일을 판단하지 않게 한다.</p>
 *
 * <p>방명록은 고친 이력을 따로 남기지 않는다. 그래서 신고 당시 본문을 여기에 적어 둔다 —
 * 신고 뒤에 고쳐 버리면 운영자는 무엇이 신고됐는지 볼 수 없다.</p>
 */
@Getter
@Entity
@Table(
		name = "guestbook_report",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_guestbook_report_entry_member",
				columnNames = {"entry_id", "member_id"}
		),
		indexes = @Index(name = "idx_guestbook_report_entry", columnList = "entry_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestbookReport extends BaseEntity {

	/** 댓글 신고(CommentReport.MAX_DETAIL_LENGTH)와 같은 값. 신고 창을 같이 쓴다 */
	public static final int MAX_DETAIL_LENGTH = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "entry_id", nullable = false)
	private GuestbookEntry entry;

	/** 신고한 사람. 관리자 화면에 내보내지 않는다 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(name = "reason", nullable = false, length = 20)
	private CommentReportReason reason;

	@Column(name = "detail", length = MAX_DETAIL_LENGTH)
	private String detail;

	/** 신고 당시 본문. 원문을 파기할 때 같이 비운다 */
	@Column(name = "reported_content", length = GuestbookEntry.MAX_CONTENT_LENGTH)
	private String reportedContent;

	/** 운영자가 이 신고를 보고 판단한 시각. 이게 없으면 이미 본 신고와 새 신고를 가릴 수 없다 */
	@Column(name = "handled_at")
	private LocalDateTime handledAt;

	public GuestbookReport(GuestbookEntry entry, Member member, CommentReportReason reason, String detail) {
		this.entry = entry;
		this.member = member;
		this.reason = reason;
		this.detail = trim(detail);
		this.reportedContent = entry.getContent();
	}

	public void markHandled(LocalDateTime now) {
		if (handledAt == null) {
			this.handledAt = now;
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
