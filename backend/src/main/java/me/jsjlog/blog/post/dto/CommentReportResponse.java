package me.jsjlog.blog.post.dto;

/**
 * 신고 접수 결과.
 *
 * <p>신고 수를 돌려주지 않는다. 화면에 "N명이 신고함" 이 보이면 그 자체로 낙인이 되고,
 * 몰려서 신고하면 숫자가 올라가는 것이 보여 재미가 붙는다. 신고한 사람에게 필요한 것은
 * 접수됐다는 사실 하나다.</p>
 */
public record CommentReportResponse(
        Long commentId,
        boolean reported
) {

    public static CommentReportResponse accepted(Long commentId) {
        return new CommentReportResponse(commentId, true);
    }
}
