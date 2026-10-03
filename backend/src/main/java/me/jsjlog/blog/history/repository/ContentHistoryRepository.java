package me.jsjlog.blog.history.repository;

import me.jsjlog.blog.history.domain.ContentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentHistoryRepository extends JpaRepository<ContentHistory, Long> {
}
