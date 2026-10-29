package me.jsjlog.blog.guestbook.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 방명록 한 줄.
 *
 * <p>운영자 답글은 따로 표를 두지 않고 이 행에 같이 둔다. 답글은 한 줄에 하나뿐이고
 * 운영자만 쓴다 — 회원끼리 말을 주고받는 자리는 글 댓글이 맡는다.</p>
 *
 * <p>지움·가림·수정 표시는 댓글({@code Comment})과 같은 뜻으로 쓴다. 같은 화면 문구를 쓰는데
 * 칸의 뜻이 다르면 회원이 두 곳에서 다른 일을 겪는다.</p>
 */
@Getter
@Entity
@Table(
		name = "guestbook_entry",
		// 공개 목록은 지우지 않은 것을 최신순으로 본다
		indexes = {
				@Index(name = "idx_guestbook_entry_deleted", columnList = "deleted, id"),
				@Index(name = "idx_guestbook_entry_member", columnList = "member_id")
		}
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestbookEntry extends BaseEntity {

	public static final int MAX_CONTENT_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(name = "content", nullable = false, length = MAX_CONTENT_LENGTH)
	private String content;

	/** 쓴 사람과 운영자만 읽는다. 운영자 답글도 같이 가려진다 */
	@Column(name = "secret", nullable = false)
	private boolean secret;

	/** 쓴 사람이 내용을 고쳤는가. 답글이 달린 뒤에 조용히 바뀌면 답글이 엉뚱한 말에 답한 것처럼 보인다 */
	@Column(name = "edited", nullable = false)
	private boolean edited;

	/** 안 보이는가. 쓴 사람이 지운 것과 운영자가 가린 것을 함께 가리킨다 */
	@Column(name = "deleted", nullable = false)
	private boolean deleted;

	/** 안 보이게 된 까닭이 운영자인가. {@code deleted} 가 거짓이면 뜻이 없다 */
	@Column(name = "hidden_by_admin", nullable = false)
	private boolean hiddenByAdmin;

	/** 쓴 사람이 지운 시각. 지운 원문의 보관 기간을 여기서부터 센다 */
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	/** 지운 원문을 파기한 시각. 값이 있으면 본문과 답글이 비어 있고 되살릴 수 없다 */
	@Column(name = "content_purged_at")
	private LocalDateTime contentPurgedAt;

	@Column(name = "reply_content", length = MAX_CONTENT_LENGTH)
	private String replyContent;

	/** 답글을 쓴 운영자. 운영자가 바뀌어도 누가 썼는지 남긴다 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reply_member_id")
	private Member replyMember;

	@Column(name = "replied_at")
	private LocalDateTime repliedAt;

	public GuestbookEntry(Member member, String content, boolean secret) {
		this.member = member;
		this.content = content;
		this.secret = secret;
		this.edited = false;
		this.deleted = false;
		this.hiddenByAdmin = false;
	}

	/** 쓴 사람이 고친다. 비밀글 여부만 바꾸면 수정 표시를 붙이지 않는다 — 말이 바뀐 것이 아니다 */
	public void update(String content, boolean secret) {
		if (!this.content.equals(content)) {
			this.content = content;
			this.edited = true;
		}
		this.secret = secret;
	}

	/** 쓴 사람이 지운다 */
	public void delete(LocalDateTime now) {
		this.deleted = true;
		this.hiddenByAdmin = false;
		this.deletedAt = now;
	}

	/** 운영자가 가린다. 쓴 사람이 이미 지운 것을 가려도 보관 기간은 늘리지 않는다 */
	public void hideByAdmin() {
		requireNotPurged();
		this.deleted = true;
		this.hiddenByAdmin = true;
	}

	/**
	 * 운영자가 자기가 가린 글을 되돌린다. 쓴 사람이 지운 글은 되살리지 않는다(2026-10-18 결정) —
	 * 지운 것은 쓴 사람의 뜻이다.
	 */
	public void restore() {
		requireNotPurged();
		if (isDeletedByWriter()) {
			throw new IllegalStateException("쓴 사람이 지운 방명록은 되살릴 수 없다");
		}
		this.deleted = false;
		this.hiddenByAdmin = false;
		this.deletedAt = null;
	}

	/**
	 * 쓴 사람이 지운 적이 있는가. 지운 뒤 운영자가 가려도 지운 시각은 남으므로 그것까지 본다 —
	 * {@code hiddenByAdmin} 만 보면 "운영자가 가린 글" 로 보여 되살리기가 통과한다.
	 */
	public boolean isDeletedByWriter() {
		return deletedAt != null || (deleted && !hiddenByAdmin);
	}

	/** 운영자 답글을 달거나 고친다. 답글은 한 줄에 하나라 새로 쓰면 앞의 것을 바꾼다 */
	public void reply(Member admin, String content, LocalDateTime now) {
		requireNotPurged();
		this.replyMember = admin;
		this.replyContent = content;
		this.repliedAt = now;
	}

	public void removeReply() {
		this.replyMember = null;
		this.replyContent = null;
		this.repliedAt = null;
	}

	public boolean hasReply() {
		return replyContent != null;
	}

	public boolean isContentPurged() {
		return contentPurgedAt != null;
	}

	public boolean isWrittenBy(Long memberId) {
		return memberId != null && member.getId().equals(memberId);
	}

	private void requireNotPurged() {
		if (isContentPurged()) {
			throw new IllegalStateException("원문을 파기한 방명록은 상태를 바꿀 수 없다");
		}
	}
}
