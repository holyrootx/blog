package me.jsjlog.blog.post.service;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 한 세션에서 이미 조회수에 반영한 글과, 조회 기록을 남긴 시각을 기억한다.
 *
 * <p>둘은 기준이 다르다. 조회수는 세션이 살아 있는 동안 글 하나에 한 번이고, 조회 기록은
 * 같은 사람이 같은 글을 30분 안에 다시 열면 한 번으로 친다. 새로고침을 거듭해도 기록이
 * 불어나지 않게 하면서, 나중에 다시 찾아 읽은 것은 남기려는 것이다.</p>
 */
final class PostViewHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    static final String SESSION_ATTRIBUTE_NAME = PostViewHistory.class.getName();

    static final Duration VISIT_WINDOW = Duration.ofMinutes(30);

    private final Set<Long> viewedPostIds = new HashSet<>();

    /**
     * 기록 단위별 마지막 기록 시각(epoch 밀리초).
     *
     * <p>로그인 전후와 계정 교체를 따로 센다. 같은 세션이라도 보는 사람이 바뀌면 다른 기록이다.
     * 이 칸이 생기기 전에 만든 세션 객체를 되살리면 비어 있을 수 있어서 쓸 때 만든다.</p>
     */
    private Map<String, Long> recordedVisits;

    boolean hasViewed(Long postId) {
        return viewedPostIds.contains(postId);
    }

    void record(Long postId) {
        viewedPostIds.add(postId);
    }

    boolean shouldRecordVisit(Long postId, Long memberId, Instant now) {
        Long recordedAt = visits().get(visitKey(postId, memberId));

        return recordedAt == null || !Instant.ofEpochMilli(recordedAt).plus(VISIT_WINDOW).isAfter(now);
    }

    void markVisitRecorded(Long postId, Long memberId, Instant now) {
        Instant cutoff = now.minus(VISIT_WINDOW);
        // 창이 지난 항목은 다시 기록해도 되는 상태라 들고 있을 이유가 없다. 오래 열린 세션이 계속 커지지 않게 한다
        visits().values().removeIf(recordedAt -> !Instant.ofEpochMilli(recordedAt).isAfter(cutoff));
        visits().put(visitKey(postId, memberId), now.toEpochMilli());
    }

    private Map<String, Long> visits() {
        if (recordedVisits == null) {
            recordedVisits = new HashMap<>();
        }
        return recordedVisits;
    }

    private static String visitKey(Long postId, Long memberId) {
        return postId + ":" + (memberId == null ? "anonymous" : memberId);
    }
}
