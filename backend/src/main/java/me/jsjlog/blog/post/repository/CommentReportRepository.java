package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.domain.CommentReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {

	boolean existsByCommentIdAndMemberId(Long commentId, Long memberId);

	List<CommentReport> findByCommentIdOrderByIdAsc(Long commentId);

	List<CommentReport> findByCommentIdAndHandledAtIsNull(Long commentId);
}
