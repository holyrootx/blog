package me.jsjlog.blog.admin.dto;

/**
 * 관리자 방명록 목록 거르기.
 *
 * <p>"답글 없음" 은 두지 않는다. 방명록은 답할 의무가 있는 자리가 아니라서, 안 단 답글을
 * 밀린 일로 세면 들어올 때마다 독촉을 보는 셈이 된다. 신고는 안 보면 계속 노출되니 따로 둔다.</p>
 */
public enum AdminGuestbookFilter {
    ALL,
    /** 아직 판단하지 않은 신고가 있다 */
    REPORTED,
    /** 운영자가 가렸다 */
    HIDDEN,
    /** 쓴 사람이 지웠다 */
    DELETED
}
