package me.jsjlog.blog.post.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PostViewHistoryTest {

    private static final Instant START = Instant.parse("2026-10-17T12:00:00Z");

    @Test
    @DisplayName("같은 사람이 같은 글을 30분 안에 다시 열면 기록하지 않고, 30분이 지나면 다시 기록한다")
    void recordsOncePerWindow() {
        PostViewHistory history = new PostViewHistory();

        assertThat(history.shouldRecordVisit(1L, null, START)).isTrue();
        history.markVisitRecorded(1L, null, START);

        assertThat(history.shouldRecordVisit(1L, null, START.plusSeconds(29 * 60 + 59))).isFalse();
        assertThat(history.shouldRecordVisit(1L, null, START.plusSeconds(30 * 60))).isTrue();
    }

    @Test
    @DisplayName("로그인 전후와 계정 교체는 같은 세션이라도 따로 기록한다")
    void viewerChangeIsSeparateVisit() {
        PostViewHistory history = new PostViewHistory();
        history.markVisitRecorded(1L, null, START);

        assertThat(history.shouldRecordVisit(1L, 10L, START.plusSeconds(1))).isTrue();
        history.markVisitRecorded(1L, 10L, START.plusSeconds(1));

        assertThat(history.shouldRecordVisit(1L, 20L, START.plusSeconds(2))).isTrue();
        assertThat(history.shouldRecordVisit(1L, 10L, START.plusSeconds(3))).isFalse();
        assertThat(history.shouldRecordVisit(2L, 10L, START.plusSeconds(3))).isTrue();
    }

    @Test
    @DisplayName("기록 표시는 조회수 반영 여부와 따로 움직인다")
    void visitWindowIsIndependentFromViewCount() {
        PostViewHistory history = new PostViewHistory();
        history.record(1L);

        assertThat(history.hasViewed(1L)).isTrue();
        assertThat(history.shouldRecordVisit(1L, null, START)).isTrue();
    }
}
