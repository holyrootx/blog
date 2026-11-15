package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.domain.PostView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PostViewRepository extends JpaRepository<PostView, Long> {

	Optional<LatestMemberView> findFirstByPostIdAndMemberIdOrderByViewedAtDescIdDesc(Long postId, Long memberId);

	/** 다른 창의 늦은 요청이 마지막 열람 시각을 과거로 되돌리지 않게 DB에서 비교한다. */
	@Modifying
	@Query("update PostView view set view.lastViewedAt = :viewedAt"
			+ " where view.id = :id and coalesce(view.lastViewedAt, view.viewedAt) < :viewedAt")
	int advanceLastViewedAt(@Param("id") Long id, @Param("viewedAt") LocalDateTime viewedAt);

	/** 누적값만 전진시켜 재전송이나 늦은 요청이 시간을 두 번 더하지 않게 한다. */
	@Modifying
	@Query("update PostView view set view.activeTimeMillis = :millis"
			+ " where view.id = :id and view.postId = :postId"
			+ " and (view.activeTimeMillis is null or view.activeTimeMillis < :millis)"
			+ " and exists (select post.id from Post post where post.id = :postId"
			+ " and post.status = me.jsjlog.blog.post.domain.PostStatus.PUBLISHED"
			+ " and post.deletedAt is null and post.publishedAt <= :now)")
	int advanceActiveTime(@Param("id") Long id, @Param("postId") Long postId,
			@Param("millis") long millis, @Param("now") LocalDateTime now);

	interface LatestMemberView {
		Long getId();
	}
}
