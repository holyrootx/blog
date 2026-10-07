package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.dto.CommentListResponse;
import me.jsjlog.blog.post.dto.CommentReplyListResponse;
import me.jsjlog.blog.post.dto.CommentContextResponse;
import java.util.Optional;

public interface CommentRepositoryCustom {

    CommentListResponse getCommentPageByPostId(Long postId, Long cursor, long size, Long memberId);

    CommentReplyListResponse getReplyPage(Long postId, Long parentId, Long cursor, long size, Long memberId);

    Optional<CommentContextResponse> getCommentContext(Long postId, Long commentId, Long memberId);
}
