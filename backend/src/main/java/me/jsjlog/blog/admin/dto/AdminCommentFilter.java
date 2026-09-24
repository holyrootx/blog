package me.jsjlog.blog.admin.dto;

public enum AdminCommentFilter {
    ALL,
    UNANSWERED,
    HIDDEN,

    /**
     * 아직 판단하지 않은 신고가 있는 댓글.
     *
     * <p>처리한 신고까지 세면 한 번 본 댓글이 목록에서 내려가지 않아, 새로 온 신고가
     * 그 사이에 묻힌다.</p>
     */
    REPORTED
}
