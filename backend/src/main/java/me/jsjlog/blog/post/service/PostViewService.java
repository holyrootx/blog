package me.jsjlog.blog.post.service;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.post.dto.PostDetailResponse;
import org.springframework.stereotype.Service;

/** 글 상세 조회와 세션당 1회 조회수 반영을 함께 조정한다. */
@Service
@RequiredArgsConstructor
public class PostViewService {

    private final PostService postService;

    public PostDetailResponse getPostDetail(Long postId, HttpSession session, Long memberId) {
        PostViewHistory history = getOrCreateHistory(session);

        // 같은 세션에서 동시에 같은 글을 요청해도 한 요청만 증가시키도록 이력 객체를 잠근다.
        synchronized (history) {
            boolean firstView = !history.hasViewed(postId);
            PostDetailResponse detail = postService.getPostDetail(postId, firstView, memberId);

            // 상세 조회 트랜잭션이 성공한 뒤에만 기록한다. 실패한 요청은 다음 조회를 막지 않는다.
            if (firstView) {
                history.record(postId);
            }

            return detail;
        }
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
