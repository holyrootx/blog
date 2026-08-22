package me.jsjlog.blog.post.dto;

public record CategoryResponse (
    Long id,
    String name,
    Long sortOrder
) {}
