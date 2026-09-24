package me.jsjlog.blog.member.dto;

import java.time.LocalDateTime;

import me.jsjlog.blog.post.domain.Comment;

/**
 * 내가 쓴 댓글 한 건.
 *
 * <p>어느 글에 썼는지가 함께 있어야 쓸모가 있다. 내용만 보면 무슨 맥락이었는지 알 수 없어서
 * 글 번호와 제목을 같이 내려주고, 화면에서 그 글로 이동하게 한다.</p>
 *
 * <p>숨겨진 댓글도 뺀 목록이 아니라 표시만 달아서 내려준다. 조용히 사라지면 본인은
 * 자기 댓글이 어디 갔는지 알 수 없다.</p>
 */
public record MyCommentResponse(
        Long id,
        Long postId,
        String postTitle,
        String content,
        LocalDateTime createdAt,
        boolean hidden,

        /**
         * 가린 것이 운영자인가.
         *
         * <p>내 댓글 목록은 이 구분이 가장 필요한 자리다. 내가 지운 것과 운영자가 가린 것을
         * 똑같이 "숨김" 이라고만 하면, 가려진 사람은 자기가 지운 줄 알고 아무것도 묻지 않는다.</p>
         */
        boolean hiddenByAdmin
) {

    public static MyCommentResponse from(Comment comment) {
        boolean hidden = comment.isDeleted();

        return new MyCommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getPost().getTitle(),
                // 숨겨진 댓글의 본문은 공개 화면에서도 안 보인다. 목록에서도 같게 다룬다
                hidden ? "" : comment.getContent(),
                comment.getCreatedAt(),
                hidden,
                hidden && comment.isHiddenByAdmin()
        );
    }
}
