package me.jsjlog.blog.post.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원이 글에서 마지막으로 읽던 자리. 회원·글마다 한 줄이다.
 *
 * <p>자리는 화면 좌표가 아니라 "본문 몇 번째 덩어리의 어디쯤" 으로 둔다. 화면 폭에 따라 줄바꿈이
 * 달라져서 좌표는 휴대폰과 PC 에서 서로 다른 곳을 가리킨다.</p>
 *
 * <p>덩어리의 글자로 만든 짧은 지문을 같이 둔다. 글을 고쳐 덩어리 순서가 바뀌거나 그 문단이
 * 사라지면 지문이 맞지 않아, 엉뚱한 곳으로 데려가지 않고 처음부터 읽게 한다.</p>
 *
 * <p>마지막으로 본 시각은 여기 두지 않는다. 그것은 조회 기록({@link PostView})에서 계산한다 —
 * 같은 조회를 두 곳에 쓰면 한쪽만 실패했을 때 둘이 어긋난다.</p>
 */
@Getter
@Entity
@Table(
		name = "post_read_position",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_post_read_position_member_post",
				columnNames = {"member_id", "post_id"}
		)
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostReadPosition {

	/** 덩어리 안에서의 위치를 천분율로 둔다. 0 이 덩어리 맨 위, 1000 이 맨 아래다 */
	public static final int MAX_BLOCK_OFFSET = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false, updatable = false)
	private Long memberId;

	@Column(name = "post_id", nullable = false, updatable = false)
	private Long postId;

	@Column(name = "block_index", nullable = false)
	private int blockIndex;

	@Column(name = "block_offset", nullable = false)
	private int blockOffset;

	@Column(name = "block_hash", nullable = false, length = 8)
	private String blockHash;

	@Column(name = "saved_at", nullable = false)
	private LocalDateTime savedAt;
}
