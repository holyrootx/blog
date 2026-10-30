package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.domain.PostReadPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PostReadPositionRepository extends JpaRepository<PostReadPosition, Long> {

	Optional<PostReadPosition> findByMemberIdAndPostId(Long memberId, Long postId);

	/**
	 * 있으면 고치고 없으면 넣는다.
	 *
	 * <p>찾아보고 넣는 두 단계로 나누면, 같은 회원이 창 두 개로 같은 글을 보다가 동시에 저장할 때
	 * 둘 다 "없다" 고 보고 넣다가 하나가 유일 제약에 걸려 실패한다. 한 문장으로 DB 가 고르게 한다.
	 * 나중에 온 저장이 이긴다.</p>
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(nativeQuery = true, value = "insert into post_read_position"
			+ " (member_id, post_id, block_index, block_offset, block_hash, saved_at)"
			+ " values (:memberId, :postId, :blockIndex, :blockOffset, :blockHash, :savedAt)"
			+ " on duplicate key update block_index = :blockIndex, block_offset = :blockOffset,"
			+ " block_hash = :blockHash, saved_at = :savedAt")
	int upsert(
			@Param("memberId") Long memberId,
			@Param("postId") Long postId,
			@Param("blockIndex") int blockIndex,
			@Param("blockOffset") int blockOffset,
			@Param("blockHash") String blockHash,
			@Param("savedAt") LocalDateTime savedAt
	);
}
