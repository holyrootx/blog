package me.jsjlog.blog.post.service;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentWriteGuardTest {
    @Test
    void limitsPerMemberAndRestoresCapacityAfterOneMinute() {
        MutableClock clock = new MutableClock();
        CommentWriteGuard guard = new CommentWriteGuard(2, clock);
        guard.check(1L);
        clock.now = clock.now.plusSeconds(30);
        guard.check(1L);
        assertThatThrownBy(() -> guard.check(1L)).isInstanceOfSatisfying(BlogException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMMENT_RATE_LIMITED));
        guard.check(2L);
        clock.now = clock.now.plusSeconds(30);
        guard.check(1L);
        assertThatThrownBy(() -> guard.check(1L)).isInstanceOf(BlogException.class);
        clock.now = clock.now.plusSeconds(30);
        guard.check(1L);
    }

    @Test
    void concurrentRequestsCannotExceedTheLimit() {
        CommentWriteGuard guard = new CommentWriteGuard(10, new MutableClock());
        AtomicInteger accepted = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 30; i++) executor.submit(() -> {
                try { guard.check(1L); accepted.incrementAndGet(); } catch (BlogException ignored) { }
            });
        }
        assertThat(accepted.get()).isEqualTo(10);
    }

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
