package me.jsjlog.blog.post.dto;

public record AdjacentPostResponse(
        AdjacentPostSummary previousPost,
        AdjacentPostSummary nextPost
){

}
