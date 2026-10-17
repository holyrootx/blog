package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.domain.PostView;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostViewRepository extends JpaRepository<PostView, Long> {
}
