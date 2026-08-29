package me.jsjlog.blog.home.repository;

import me.jsjlog.blog.home.domain.HomePageTopic;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomePageTopicRepository extends JpaRepository<HomePageTopic, Long> {
}
