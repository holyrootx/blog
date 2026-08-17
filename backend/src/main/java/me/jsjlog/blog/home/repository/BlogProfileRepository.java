package me.jsjlog.blog.home.repository;

import me.jsjlog.blog.home.domain.BlogProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogProfileRepository extends JpaRepository<BlogProfile,Long> {

}
