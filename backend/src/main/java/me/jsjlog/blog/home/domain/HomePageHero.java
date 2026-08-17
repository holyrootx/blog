package me.jsjlog.blog.home.domain;

import me.jsjlog.blog.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "home_page_hero")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HomePageHero extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sub_title", nullable = false, length = 255)
    private String subTitle;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "intro", nullable = false, columnDefinition = "TEXT")
    private String intro;

    @Column(name = "hero_image_url", length = 500)
    private String heroImageUrl;

    public HomePageHero(
            String subTitle,
            String title,
            String intro,
            String heroImageUrl
    ) {
        this.subTitle = subTitle;
        this.title = title;
        this.intro = intro;
        this.heroImageUrl = heroImageUrl;
    }

    public void update(
            String subTitle,
            String title,
            String intro,
            String heroImageUrl
    ) {
        this.subTitle = subTitle;
        this.title = title;
        this.intro = intro;
        this.heroImageUrl = heroImageUrl;
    }
}
