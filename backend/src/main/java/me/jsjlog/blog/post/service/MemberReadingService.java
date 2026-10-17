package me.jsjlog.blog.post.service;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.post.dto.ReadingPositionRequest;
import me.jsjlog.blog.post.dto.ReadingPositionResponse;
import me.jsjlog.blog.post.dto.RecentPostListResponse;
import me.jsjlog.blog.post.dto.ViewedPostResponse;
import me.jsjlog.blog.post.repository.MemberReadingQueryRepository;
import me.jsjlog.blog.post.repository.PostReadPositionRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 회원 본인의 읽기 기록.
 *
 * <p>회원 번호는 언제나 로그인 정보에서만 받는다. 요청 값으로 받으면 남의 번호를 넣어 남이
 * 무엇을 읽었는지 볼 수 있게 된다.</p>
 */
@Service
@RequiredArgsConstructor
public class MemberReadingService {

    private static final int DEFAULT_RECENT_SIZE = 10;
    private static final int MAX_RECENT_SIZE = 50;

    /** 목록 한 화면의 카드 수보다 넉넉하게. 한 번에 많이 물으면 IN 절이 길어진다 */
    private static final int MAX_VIEWED_POST_IDS = 50;

    private final MemberReadingQueryRepository memberReadingQueryRepository;
    private final PostReadPositionRepository postReadPositionRepository;
    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public RecentPostListResponse getRecentPosts(Long memberId, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? DEFAULT_RECENT_SIZE : Math.min(size, MAX_RECENT_SIZE);

        return RecentPostListResponse.of(
                memberReadingQueryRepository.getRecentPosts(memberId, pageNumber, pageSize),
                pageNumber,
                pageSize,
                memberReadingQueryRepository.countRecentPosts(memberId)
        );
    }

    @Transactional(readOnly = true)
    public List<ViewedPostResponse> getViewedPosts(Long memberId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }

        Set<Long> ids = new LinkedHashSet<>(postIds.stream().filter(Objects::nonNull).toList());
        if (ids.size() > MAX_VIEWED_POST_IDS) {
            throw new BlogException(ErrorCode.INVALID_INPUT);
        }

        return ids.isEmpty() ? List.of() : memberReadingQueryRepository.getViewedPosts(memberId, ids);
    }

    /** 저장한 자리가 없으면 비어 있다. 공개되지 않은 글은 없는 글과 같이 답한다 */
    @Transactional(readOnly = true)
    public ReadingPositionResponse getReadingPosition(Long memberId, Long postId) {
        requireReadablePost(postId);

        return postReadPositionRepository.findByMemberIdAndPostId(memberId, postId)
                .map(ReadingPositionResponse::from)
                .orElse(null);
    }

    @Transactional
    public void saveReadingPosition(Long memberId, Long postId, ReadingPositionRequest request) {
        requireReadablePost(postId);

        postReadPositionRepository.upsert(
                memberId,
                postId,
                request.blockIndex(),
                request.blockOffset(),
                request.blockHash(),
                LocalDateTime.now()
        );
    }

    private void requireReadablePost(Long postId) {
        boolean readable = postRepository.findById(postId)
                .map(post -> post.isPubliclyVisible(LocalDateTime.now()))
                .orElse(false);

        if (!readable) {
            throw new BlogException(ErrorCode.POST_NOT_FOUND);
        }
    }
}
