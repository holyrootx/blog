package me.jsjlog.blog.home.dto;

import me.jsjlog.blog.home.domain.BlogProfile;

import java.time.LocalDate;

public record BlogProfileResponse (
        Long id,
        String name,
        String intro,
        String job,
        String avatarImageUrl,
        String githubUrl,
        String email,
        LocalDate blogStartedAt,
        Long daysSinceStart
) {
    public static BlogProfileResponse from(BlogProfile blogProfile) {
        return new BlogProfileResponse(
                blogProfile.getId(),
                blogProfile.getName(),
                blogProfile.getIntro(),
                blogProfile.getJob(),
                blogProfile.getAvatarImageUrl(),
                blogProfile.getGithubUrl(),
                blogProfile.getEmail(),
                blogProfile.getBlogStartedAt(),
                blogProfile.daysSinceStart(LocalDate.now())
        );
    }
}
