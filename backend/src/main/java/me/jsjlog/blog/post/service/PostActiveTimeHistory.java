package me.jsjlog.blog.post.service;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;

/**
 * 조회 행에 연결된 측정 요청만 받는 세션 내 장부. 호출자는 PostViewHistory를 잠근다.
 * 페이지별 누적값의 증가분을 합치되 조회 이후 실제 경과 시간보다 많이 더하지 않는다.
 * 토큰과 수신 위치는 세션에만 있고 익명 방문자를 식별하는 용도로 쓰지 않는다.
 */
final class PostActiveTimeHistory implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;
	static final Duration LIFETIME = Duration.ofHours(2);
	private static final int MAX_ENTRIES = 128;
	private final LinkedHashMap<String, Visit> visits = new LinkedHashMap<>();
	private final LinkedHashMap<String, Page> pages = new LinkedHashMap<>();

	void recordVisit(Long postId, Long memberId, Long viewId, Instant now) {
		prune(now);
		visits.put(key(postId, memberId), new Visit(viewId, postId, memberId, now));
		trim(visits);
	}

	String start(Long postId, Long memberId, Instant now) {
		prune(now);
		Visit visit = visits.get(key(postId, memberId));
		if (visit == null) {
			return null;
		}
		String token = UUID.randomUUID().toString();
		pages.put(token, new Page(visit, now));
		trim(pages);
		return token;
	}

	Update prepare(Long postId, Long memberId, String token, long millis, Instant now) {
		prune(now);
		Page page = pages.get(token);
		if (page == null || !page.visit.postId.equals(postId) || !Objects.equals(page.visit.memberId, memberId)
				|| millis < page.receivedMillis || millis < 0 || millis > LIFETIME.toMillis()
				|| millis > Duration.between(page.startedAt, now).toMillis() + 1000) {
			return null;
		}
		long elapsed = Math.max(0, Duration.between(page.visit.startedAt, now).toMillis());
		long total = Math.min(page.visit.totalMillis + millis - page.receivedMillis,
				Math.min(elapsed, LIFETIME.toMillis()));
		return new Update(token, page.visit.viewId, millis, Math.max(page.visit.totalMillis, total));
	}

	void accept(Update update) {
		Page page = pages.get(update.token());
		page.receivedMillis = update.receivedMillis();
		page.visit.totalMillis = update.totalMillis();
	}

	private void prune(Instant now) {
		visits.values().removeIf(visit -> !visit.startedAt.plus(LIFETIME).isAfter(now));
		pages.values().removeIf(page -> !page.visit.startedAt.plus(LIFETIME).isAfter(now));
	}

	private static void trim(LinkedHashMap<?, ?> map) {
		while (map.size() > MAX_ENTRIES) {
			map.pollFirstEntry();
		}
	}

	private static String key(Long postId, Long memberId) {
		return postId + ":" + memberId;
	}

	record Update(String token, Long viewId, long receivedMillis, long totalMillis) {
	}

	private static final class Visit implements Serializable {
		@Serial private static final long serialVersionUID = 1L;
		private final Long viewId;
		private final Long postId;
		private final Long memberId;
		private final Instant startedAt;
		private long totalMillis;

		private Visit(Long viewId, Long postId, Long memberId, Instant startedAt) {
			this.viewId = viewId;
			this.postId = postId;
			this.memberId = memberId;
			this.startedAt = startedAt;
		}
	}

	private static final class Page implements Serializable {
		@Serial private static final long serialVersionUID = 1L;
		private final Visit visit;
		private final Instant startedAt;
		private long receivedMillis;

		private Page(Visit visit, Instant startedAt) {
			this.visit = visit;
			this.startedAt = startedAt;
		}
	}
}
