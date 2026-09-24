package me.jsjlog.blog.search.repository;

import me.jsjlog.blog.search.domain.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {
}
