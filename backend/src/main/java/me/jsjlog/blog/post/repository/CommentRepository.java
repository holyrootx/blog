package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
}
