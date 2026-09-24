package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.domain.CommentReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {

    boolean existsByCommentIdAndMemberId(Long commentId, Long memberId);

    /** 관리자 화면에 보여 줄 신고 내역. 온 순서대로 */
    List<CommentReport> findByCommentIdOrderByIdAsc(Long commentId);

    /** 아직 판단하지 않은 신고. 조치할 때 한꺼번에 처리로 바꾼다 */
    List<CommentReport> findByCommentIdAndHandledAtIsNull(Long commentId);
}
