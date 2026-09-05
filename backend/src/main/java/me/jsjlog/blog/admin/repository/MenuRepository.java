package me.jsjlog.blog.admin.repository;

import me.jsjlog.blog.admin.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu,Long>, MenuRepositoryCustom {

}
