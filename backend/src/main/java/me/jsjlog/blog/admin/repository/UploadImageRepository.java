package me.jsjlog.blog.admin.repository;

import me.jsjlog.blog.admin.domain.UploadImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadImageRepository extends JpaRepository<UploadImage, Long> {
}
