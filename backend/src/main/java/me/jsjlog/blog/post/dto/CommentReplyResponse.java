package me.jsjlog.blog.post.dto;

import java.time.LocalDateTime;

public record CommentReplyResponse(
        Long id,
        String nickname,
        String content,
        LocalDateTime createdAt,
        boolean authorComment,
        boolean deleted,
        long likeCount,
        long dislikeCount,
        boolean likedByMe,
        boolean dislikedByMe,

        /** 내가 쓴 답글인가. 화면이 신고 단추를 감추는 데 쓴다 — 막는 일은 서버가 한다 */
        boolean mine,

        /** 글쓴이가 내용을 고쳤는가 */
        boolean edited,

        /** 내가 이미 신고한 답글인가 */
        boolean reportedByMe
) {
}
