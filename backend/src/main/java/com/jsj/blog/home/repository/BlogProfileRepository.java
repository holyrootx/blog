package com.jsj.blog.home.repository;

import com.jsj.blog.home.domain.BlogProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogProfileRepository extends JpaRepository<BlogProfile,Long> {

}
