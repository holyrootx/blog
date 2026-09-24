package me.jsjlog.blog.post.dto;

/**
 * 내 댓글 고치기 요청.
 *
 * <p>내용만 받는다. 어느 글의 댓글인지, 누구의 댓글인지는 서버가 이미 알고 있고,
 * 요청이 바꿀 수 있게 열어 두면 남의 댓글을 가리켜 고칠 길이 생긴다.</p>
 */
public record CommentUpdateRequest(String content) {
}
