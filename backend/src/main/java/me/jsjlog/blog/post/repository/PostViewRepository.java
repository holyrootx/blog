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

	interface LatestMemberView {
		Long getId();
	}
}
