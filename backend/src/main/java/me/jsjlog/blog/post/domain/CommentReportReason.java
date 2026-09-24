package me.jsjlog.blog.post.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 신고 사유.
 *
 * <p>고르게 하는 이유는 신고를 받는 쪽 때문이다. 자유롭게 적게 하면 무엇이 문제라는
 * 것인지 읽어 봐야 알 수 있고, 급한 것과 아닌 것을 가릴 수 없다.</p>
 *
 * <p>가짓수를 넷으로 묶은 것은 더 잘게 나눠 봐야 신고하는 사람이 고르기만 어려워지기
 * 때문이다. 어디에도 안 맞는 것은 {@link #OTHER} 로 받고 한 줄 설명을 덧붙이게 한다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum CommentReportReason {

    SPAM("광고·도배"),
    ABUSE("욕설·비방"),
    ADULT("음란물"),
    OTHER("기타");

    private final String label;
}
