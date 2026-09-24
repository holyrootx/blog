package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.domain.CommentModeration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentModerationRepository extends JpaRepository<CommentModeration, Long> {

    /** 최근에 한 조치가 위로. 무엇이 마지막 판단인지가 먼저 보여야 한다 */
    List<CommentModeration> findByCommentIdOrderByActedAtDescIdDesc(Long commentId);
}
