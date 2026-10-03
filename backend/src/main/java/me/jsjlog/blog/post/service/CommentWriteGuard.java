package me.jsjlog.blog.post.service;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** 단일 서버에서 회원별 댓글·답글 등록을 제한한다. 클라이언트 IP 헤더는 신뢰하지 않는다. */
@Component
public class CommentWriteGuard {
    private static final int MAX_MEMBERS = 10_000;
    private final int limit;
    private final Clock clock;
    private final Map<Long, ArrayDeque<Instant>> attempts = new HashMap<>();

    @Autowired
    public CommentWriteGuard(@Value("${blog.comments.writes-per-minute:10}") int limit) {
        this(limit, Clock.systemUTC());
    }

    CommentWriteGuard(int limit, Clock clock) {
        if (limit < 1) throw new IllegalArgumentException("댓글 등록 제한은 1 이상이어야 합니다.");
        this.limit = limit;
        this.clock = clock;
    }

    public synchronized void check(Long memberId) {
        if (memberId == null) throw new BlogException(ErrorCode.UNAUTHORIZED);
        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofMinutes(1));
        attempts.values().forEach(queue -> {
            while (!queue.isEmpty() && !queue.getFirst().isAfter(cutoff)) queue.removeFirst();
        });
        attempts.values().removeIf(ArrayDeque::isEmpty);
        if (!attempts.containsKey(memberId) && attempts.size() >= MAX_MEMBERS) {
            throw new BlogException(ErrorCode.COMMENT_RATE_LIMITED);
        }
        ArrayDeque<Instant> queue = attempts.computeIfAbsent(memberId, ignored -> new ArrayDeque<>());
        if (queue.size() >= limit) throw new BlogException(ErrorCode.COMMENT_RATE_LIMITED);
        queue.addLast(now);
    }
}
