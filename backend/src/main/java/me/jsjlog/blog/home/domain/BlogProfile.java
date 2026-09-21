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

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Getter
@Entity
@Table(name = "blog_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlogProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "intro", nullable = false, length = 500)
    private String intro;

    @Column(name = "job", nullable = false, length = 100)
    private String job;

    @Column(name = "avatar_image_url", length = 500)
    private String avatarImageUrl;

    @Column(name = "github_url", length = 500)
    private String githubUrl;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "blog_started_at")
    private LocalDate blogStartedAt;

    public BlogProfile(
            String name,
            String intro,
            String job,
            String avatarImageUrl,
            String githubUrl,
            String email,
            LocalDate blogStartedAt
    ) {
        this.name = name;
        this.intro = intro;
        this.job = job;
        this.avatarImageUrl = avatarImageUrl;
        this.githubUrl = githubUrl;
        this.email = email;
        this.blogStartedAt = blogStartedAt;
    }

    public void update(
            String name,
            String intro,
            String job,
            String avatarImageUrl,
            String githubUrl,
            String email,
            LocalDate blogStartedAt
    ) {
        this.name = name;
        this.intro = intro;
        this.job = job;
        this.avatarImageUrl = avatarImageUrl;
        this.githubUrl = githubUrl;
        this.email = email;
        this.blogStartedAt = blogStartedAt;
    }

    /** 시작한 날을 1일째로 센다. 시작일이 없으면 대시보드도 값을 표시하지 않는다. */
    public Long daysSinceStart(LocalDate today) {
        if (blogStartedAt == null) {
            return null;
        }

        return ChronoUnit.DAYS.between(blogStartedAt, today) + 1;
    }

}
