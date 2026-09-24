package me.jsjlog.blog.post.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CommentItemResponse(
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

        /**
         * 내가 쓴 댓글인가.
         *
         * <p>화면이 신고 단추를 감추는 데 쓴다. 자기 글을 신고하는 것은 뜻이 없고,
         * 눌러 봐야 거절만 되는 단추를 보여 줄 이유가 없다.</p>
         *
         * <p>막는 일 자체는 서버가 한다. 이건 헛걸음을 줄이는 표시지 검사가 아니다.</p>
         */
        boolean mine,

        /** 글쓴이가 내용을 고쳤는가. 화면이 "수정됨" 을 붙이는 데 쓴다 */
        boolean edited,

        /**
         * 안 보이는 까닭이 운영자인가.
         *
         * <p>화면에 뭐라고 쓸지가 갈린다. 글쓴이가 지운 것을 "운영자가 가렸다" 고 하면
         * 없는 일을 만들고, 운영자가 가린 것을 "삭제된 댓글" 이라고 하면 글쓴이가
         * 자기가 지운 줄 안다.</p>
         */
        boolean hiddenByAdmin,

        /**
         * 내가 이미 신고한 댓글인가.
         *
         * <p>없으면 다시 눌러 봐야 "이미 신고한 댓글입니다" 로 알게 된다. 신고는 한 번이면
         * 되는 일이라, 눌러도 되는 것처럼 보이는 단추를 남겨 둘 이유가 없다.</p>
         */
        boolean reportedByMe,

        List<CommentReplyResponse> replies
) {
}
