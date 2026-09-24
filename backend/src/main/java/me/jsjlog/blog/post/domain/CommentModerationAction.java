package me.jsjlog.blog.post.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 운영자가 댓글에 한 조치.
 *
 * <p>{@link #DISMISS} 가 따로 있는 이유는, 신고를 봤는데 문제가 없더라는 것도 판단이기
 * 때문이다. 그것을 남길 자리가 없으면 같은 신고를 볼 때마다 처음부터 다시 읽게 된다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum CommentModerationAction {

    HIDE("가림"),
    RESTORE("되돌림"),
    DISMISS("문제 없음");

    private final String label;
}
