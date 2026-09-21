package me.jsjlog.blog.notification.domain;

/**
 * 알림 종류.
 *
 * <p>둘 다 "나를 기다리는 일" 이다. 안 보면 대화가 끊긴다.
 * 좋아요는 넣지 않는다 — 안 봐도 아무 일이 일어나지 않고, 열 개 눌리면 알림이 열 개가 되어
 * 묶는 처리를 따로 만들어야 한다. 싫어요는 어느 경우에도 알리지 않는다.
 * 기분만 상하고 할 수 있는 일이 없다.</p>
 */
public enum NotificationType {

    /** 내 댓글에 답글이 달렸다 */
    REPLY,

    /** 내 글에 댓글이 달렸다 */
    POST_COMMENT
}
