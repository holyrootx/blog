package me.jsjlog.blog.home.dto;

import java.time.LocalDate;

public record UpdateBlogProfileRequest(
        Long id,
        String name,
        String intro,
        String job,
        String avatarImageUrl,
        String githubUrl,
        String email,
        LocalDate blogStartedAt
){

}
