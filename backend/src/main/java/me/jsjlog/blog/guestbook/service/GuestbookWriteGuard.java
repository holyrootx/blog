package me.jsjlog.blog.guestbook.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 회원별 방명록 등록을 1분에 정해진 횟수로 막는다. 단일 서버 기준이고 클라이언트 IP 헤더는 믿지 않는다.
 *
 * <p>댓글({@code CommentWriteGuard})과 같은 방식이고 숫자만 더 낮다. 방명록은 인사 한 줄을 남기는
 * 자리라 짧은 시간에 여러 번 쓸 까닭이 없다.</p>
 */
@Component
public class GuestbookWriteGuard {

    private static final int MAX_MEMBERS = 10_000;

    private final int limit;
    private final Clock clock;
    private final Map<Long, ArrayDeque<Instant>> attempts = new HashMap<>();

    @Autowired
    public GuestbookWriteGuard(@Value("${blog.guestbook.writes-per-minute:3}") int limit) {
        this(limit, Clock.systemUTC());
    }

    GuestbookWriteGuard(int limit, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("방명록 등록 제한은 1 이상이어야 합니다.");
        }
        this.limit = limit;
        this.clock = clock;
    }

    public synchronized void check(Long memberId) {
        if (memberId == null) {
            throw new BlogException(ErrorCode.UNAUTHORIZED);
        }

        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofMinutes(1));
        attempts.values().forEach(queue -> {
            while (!queue.isEmpty() && !queue.getFirst().isAfter(cutoff)) {
                queue.removeFirst();
            }
        });
        attempts.values().removeIf(ArrayDeque::isEmpty);

        // 기억할 회원 수에 끝이 없으면 그 자체가 메모리를 채우는 길이 된다
        if (!attempts.containsKey(memberId) && attempts.size() >= MAX_MEMBERS) {
            throw new BlogException(ErrorCode.GUESTBOOK_RATE_LIMITED);
        }

        ArrayDeque<Instant> queue = attempts.computeIfAbsent(memberId, ignored -> new ArrayDeque<>());
        if (queue.size() >= limit) {
            throw new BlogException(ErrorCode.GUESTBOOK_RATE_LIMITED);
        }
        queue.addLast(now);
    }
}
