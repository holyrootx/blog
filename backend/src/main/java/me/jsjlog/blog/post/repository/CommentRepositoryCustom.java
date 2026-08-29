package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.dto.CommentListResponse;

public interface CommentRepositoryCustom {

    CommentListResponse getCommentPageByPostId(Long postId, Long cursor, long size);
}
