package me.jsjlog.blog.post.service;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.post.domain.DeviceType;
import me.jsjlog.blog.post.dto.PostDetailResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

/** 글 상세 조회와 세션당 1회 조회수 반영, 조회 기록을 함께 조정한다. */
@Slf4j
@Service
public class PostViewService {

	private final PostService postService;
	private final PostViewRecorder postViewRecorder;
	private final Clock clock;

	@Autowired
	public PostViewService(PostService postService, PostViewRecorder postViewRecorder) {
		this(postService, postViewRecorder, Clock.systemDefaultZone());
	}

	PostViewService(PostService postService, PostViewRecorder postViewRecorder, Clock clock) {
		this.postService = postService;
		this.postViewRecorder = postViewRecorder;
		this.clock = clock;
	}

	public PostDetailResponse getPostDetail(Long postId, HttpSession session, Long memberId, DeviceType deviceType) {
		PostViewHistory history = getOrCreateHistory(session);

		// 같은 세션에서 동시에 같은 글을 요청해도 한 요청만 증가시키도록 이력 객체를 잠근다.
		synchronized (history) {
			boolean firstView = !history.hasViewed(postId);
			PostDetailResponse detail = postService.getPostDetail(postId, firstView, memberId);

			// 상세 조회 트랜잭션이 성공한 뒤에만 기록한다. 실패한 요청은 다음 조회를 막지 않는다.
			if (firstView) {
				history.record(postId);
			}
			recordVisit(history, postId, memberId, deviceType);

			return detail;
		}
	}

	/**
	 * 조회 기록은 덤이다. 이것 때문에 글을 못 읽으면 본말이 뒤집힌다.
	 *
	 * <p>저장에 성공했을 때만 세션에 표시한다. 실패한 기록을 표시하면 30분 동안 다시 시도하지 않는다.
	 * 빠진 기록은 로그로만 알 수 있으므로 경고로 남긴다.</p>
	 */
	private void recordVisit(PostViewHistory history, Long postId, Long memberId, DeviceType deviceType) {
		Instant now = clock.instant();
		try {
			LocalDateTime viewedAt = LocalDateTime.ofInstant(now, clock.getZone());
			if (history.shouldRecordVisit(postId, memberId, now)) {
				Long viewId = postViewRecorder.record(postId, memberId, viewedAt, deviceType);
				history.markVisitRecorded(postId, memberId, now);
				if (deviceType != DeviceType.BOT) {
					history.activeTime().recordVisit(postId, memberId, viewId, now);
				}
			} else if (memberId != null) {
				postViewRecorder.updateLastViewedAt(postId, memberId, viewedAt);
			}
		} catch (RuntimeException e) {
			log.warn("[recordVisit] 조회 기록 실패. postId={}, memberId={}", postId, memberId, e);
		}
	}

	/** 기존 세션의 조회에만 연결한다. 토큰은 DB나 쿠키에 저장하는 방문자 식별자가 아니다. */
	public String startActiveTime(Long postId, HttpSession session, Long memberId) {
		PostViewHistory history = existingHistory(session);
		if (history == null) {
			return null;
		}
		synchronized (history) {
			return history.activeTime().start(postId, memberId, clock.instant());
		}
	}

	public void recordActiveTime(Long postId, HttpSession session, Long memberId, String token, long millis) {
		PostViewHistory history = existingHistory(session);
		if (history == null) {
			return;
		}
		synchronized (history) {
			Instant now = clock.instant();
			var update = history.activeTime().prepare(postId, memberId, token, millis, now);
			if (update == null) {
				return;
			}
			// 커밋 뒤에만 세션의 수신 위치를 옮긴다. 실패하면 다음 누적 전송으로 다시 시도한다.
			postViewRecorder.updateActiveTime(update.viewId(), postId, update.totalMillis(),
					LocalDateTime.ofInstant(now, clock.getZone()));
			history.activeTime().accept(update);
		}
	}

	private PostViewHistory existingHistory(HttpSession session) {
		if (session == null) {
			return null;
		}
		Object value = session.getAttribute(PostViewHistory.SESSION_ATTRIBUTE_NAME);
		return value instanceof PostViewHistory history ? history : null;
	}

	private PostViewHistory getOrCreateHistory(HttpSession session) {
		synchronized (session) {
			Object attribute = session.getAttribute(PostViewHistory.SESSION_ATTRIBUTE_NAME);
			if (attribute instanceof PostViewHistory history) {
				return history;
			}

			PostViewHistory history = new PostViewHistory();
			session.setAttribute(PostViewHistory.SESSION_ATTRIBUTE_NAME, history);
			return history;
		}
	}
}
