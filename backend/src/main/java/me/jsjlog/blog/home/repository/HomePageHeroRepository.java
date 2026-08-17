package me.jsjlog.blog.home.repository;

import me.jsjlog.blog.home.domain.HomePageHero;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomePageHeroRepository extends JpaRepository<HomePageHero, Long> {

}
