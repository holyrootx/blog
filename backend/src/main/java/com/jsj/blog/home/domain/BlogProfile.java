package com.jsj.blog.home.domain;

import com.jsj.blog.common.domain.BaseEntity;

import com.jsj.blog.home.dto.UpdateBlogProfileRequest;
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

    public BlogProfile(
            String name,
            String intro,
            String job,
            String avatarImageUrl,
            String githubUrl,
            String email
    ) {
        this.name = name;
        this.intro = intro;
        this.job = job;
        this.avatarImageUrl = avatarImageUrl;
        this.githubUrl = githubUrl;
        this.email = email;
    }

    public void update(
            String name,
            String intro,
            String job,
            String avatarImageUrl,
            String githubUrl,
            String email
    ) {
        this.name = name;
        this.intro = intro;
        this.job = job;
        this.avatarImageUrl = avatarImageUrl;
        this.githubUrl = githubUrl;
        this.email = email;
    }

}