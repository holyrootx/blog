package com.jsj.blog.home.dto;

public record UpdateBlogProfileRequest(
        Long id,
        String name,
        String intro,
        String job,
        String avatarImageUrl,
        String githubUrl,
        String email
){

}