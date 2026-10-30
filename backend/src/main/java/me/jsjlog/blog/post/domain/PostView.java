package me.jsjlog.blog.post.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 공개 글 상세를 한 번 연 기록.
 *
 * <p>관리자 조회 기록과 기간별 통계는 최초 기록 시각을 읽는다. 회원의 최근 본 글은 마지막 열람
 * 시각을 읽어, 30분 안에 다시 열어도 집계 건수를 늘리지 않고 실제 재방문 시각을 표시한다.</p>
 *
 * <p>{@code Post.views} 와 기준이 다르다. 그쪽은 세션마다 글 하나에 한 번만 오르는 누적값이고,
 * 이 기록은 같은 세션이라도 30분이 지나면 다시 쌓인다. 배포 전 누적값을 이 기록으로 되살리지 않는다.</p>
 *
 * <p><b>누가 봤는지는 회원 번호까지만 남긴다.</b> 비회원은 번호 없이 시각만 남는다. IP, 브라우저 정보,
 * 세션 ID 는 두지 않는다 — 회원이 아닌 사람을 알아볼 단서를 모으지 않는다.</p>
 *
 * <p>글·회원과 외래키로 묶지 않는다. 조회가 몰리면 이 INSERT 가 글 행의 잠금을 기다리게 되고,
 * 글과 회원은 지우지 않고 남기는 구조라 묶지 않아도 가리키는 대상이 사라지지 않는다.
 * 집계용 최초 기록 시각은 변경하지 않는다.</p>
 */
@Getter
@Entity
@Table(
		name = "post_view",
		indexes = {
				// 관리자 글별 기록을 최근순으로 읽는다
				@Index(name = "idx_post_view_post", columnList = "post_id, viewed_at"),
				// 회원의 최근 본 글과 카드 표시가 회원·글 단위로 마지막 시각을 찾는다
				@Index(name = "idx_post_view_member", columnList = "member_id, post_id, viewed_at"),
				// 관리자 통계가 최근 7일·30일을 잘라 센다
				@Index(name = "idx_post_view_viewed_at", columnList = "viewed_at")
		}
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostView {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id", nullable = false, updatable = false)
	private Long postId;

	/** 비회원이면 비어 있다 */
	@Column(name = "member_id", updatable = false)
	private Long memberId;

	@Column(name = "viewed_at", nullable = false, updatable = false)
	private LocalDateTime viewedAt;

	/** 회원의 실제 마지막 열람. 기존 기록은 null이면 viewedAt으로 읽는다. */
	@Column(name = "last_viewed_at")
	private LocalDateTime lastViewedAt;

	/** 거칠게 접은 기기 분류(검색 봇 포함). 원래 브라우저 정보는 남기지 않는다 */
	@Enumerated(EnumType.STRING)
	@Column(
			name = "device_type",
			nullable = false,
			updatable = false,
			length = 10,
			columnDefinition = "varchar(10) not null default 'UNKNOWN'"
	)
	private DeviceType deviceType;

	private PostView(Long postId, Long memberId, LocalDateTime viewedAt, DeviceType deviceType) {
		this.postId = postId;
		this.memberId = memberId;
		this.viewedAt = viewedAt;
		this.lastViewedAt = memberId == null ? null : viewedAt;
		this.deviceType = deviceType == null ? DeviceType.UNKNOWN : deviceType;
	}

	public static PostView of(Long postId, Long memberId, LocalDateTime viewedAt) {
		return new PostView(postId, memberId, viewedAt, DeviceType.UNKNOWN);
	}

	public static PostView of(Long postId, Long memberId, LocalDateTime viewedAt, DeviceType deviceType) {
		return new PostView(postId, memberId, viewedAt, deviceType);
	}
}
