package me.jsjlog.blog.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.admin.dto.AdminPostDetailResponse;
import me.jsjlog.blog.admin.dto.AdminPostListResponse;
import me.jsjlog.blog.admin.dto.AdminPostRequest;
import me.jsjlog.blog.admin.dto.AdminPostSearchCondition;
import me.jsjlog.blog.admin.dto.AdminPostStatusCounts;
import me.jsjlog.blog.admin.dto.AdminPostSummaryResponse;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ConcurrencyGuard;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentReactionRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.post.repository.PostReactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 관리자 글 관리.
 * 클래스명에 Admin 을 붙인 이유는 post.service.PostService 와 빈 이름이 겹치면
 * 패키지가 달라도 부팅이 실패하기 때문이다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminPostService {

    private static final int TITLE_MAX = 255;
    private static final int EXCERPT_MAX = 500;
    private static final int THUMBNAIL_MAX = 500;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final PostReactionRepository postReactionRepository;
    private final CategoryRepository categoryRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public AdminPostListResponse getPostList(AdminPostSearchCondition condition) {

        List<AdminPostSummaryResponse> items = postRepository.getAdminPosts(condition);
        long totalElements = postRepository.countAdminPosts(condition);
        AdminPostStatusCounts statusCounts = postRepository.countByStatus(condition);

        int size = condition.sizeOrDefault();

        return new AdminPostListResponse(
                items,
                statusCounts,
                condition.pageOrDefault(),
                size,
                totalElements,
                (int) Math.ceil((double) totalElements / size)
        );
    }

    /**
     * 편집 화면이 읽는 글 한 건.
     * category 가 LAZY 라 트랜잭션 안에서 읽어야 한다.
     * OSIV 에 기대면 설정 하나로 조용히 깨진다
     */
    @Transactional(readOnly = true)
    public AdminPostDetailResponse getPost(Long postId) {
        Post post = findPost(postId);

        return new AdminPostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getCategory().getId(),
                post.getCategory().getName(),
                post.getExcerpt(),
                post.getContent(),
                post.getThumbnailImageUrl(),
                post.getStatus(),
                post.getPublishedAt(),
                post.getUpdatedAt()
        );
    }

    /**
     * 작성 (임시저장).
     *
     * 본문과 요약은 비어 있어도 된다. 임시저장의 목적은 "지금 쓴 것을 잃지 않는 것"이라,
     * 여기에 검증을 걸면 제목만 떠오른 상태로는 저장을 못 하고 그대로 날아간다.
     * 대신 길이 검증은 임시저장에도 건다 — 컬럼 길이를 넘기면 500이 나고
     * 사용자 눈에는 "저장했는데 아무 일도 안 일어남 + 내용 유실"로 보인다.
     */
    @Transactional
    public Long createPost(AdminPostRequest request, Long authorId) {
        String title = requireTitle(request.title());
        checkLengths(request);

        Post post = new Post(
                title,
                request.excerpt(),
                // content 가 NOT NULL 이라 null 로는 INSERT 가 안 된다
                Objects.requireNonNullElse(request.content(), ""),
                findCategory(request.categoryId()),
                request.thumbnailImageUrl(),
                findAuthor(authorId)
        );

        return postRepository.save(post).getId();
    }

    /**
     * 수정. status 는 건드리지 않는다 — 발행은 별도 엔드포인트다.
     *
     * 발행된 글은 발행 조건을 계속 만족해야 한다.
     * 이미 공개된 글에서 요약을 지우고 저장할 수 있으면 공개 화면의 카드가 빈다.
     */
    @Transactional
    public void updatePost(Long postId, AdminPostRequest request) {
        Post post = findPost(postId);

        // 화면이 불러온 뒤 다른 곳에서 바뀌었으면 덮어쓰지 않는다
        ConcurrencyGuard.check(request.updatedAt(), post.getUpdatedAt());

        String title = requireTitle(request.title());
        checkLengths(request);

        if (post.isPublished()) {
            checkPublishable(request.content(), request.excerpt());
        }

        // 글쓴이는 여기서 건드리지 않는다. 생성할 때 한 번 정해지는 값이고,
        // 저장할 때마다 바꾸면 남의 글을 고친 사람이 글쓴이가 된다
        post.update(
                title,
                request.excerpt(),
                Objects.requireNonNullElse(request.content(), ""),
                findCategory(request.categoryId()),
                request.thumbnailImageUrl()
        );
    }

    /** 발행 상태를 유지하려면 본문과 요약이 있어야 한다 */
    private void checkPublishable(String content, String excerpt) {
        if (!StringUtils.hasText(content)) {
            throw new BlogException(ErrorCode.POST_CONTENT_REQUIRED);
        }

        if (!StringUtils.hasText(excerpt)) {
            throw new BlogException(ErrorCode.POST_EXCERPT_REQUIRED);
        }
    }

    private String requireTitle(String title) {
        if (!StringUtils.hasText(title)) {
            // 공백만 있는 제목은 목록에서 빈 행이 된다
            throw new BlogException(ErrorCode.POST_TITLE_REQUIRED);
        }

        return title.trim();
    }

    /** 컬럼 길이를 넘기면 저장이 500으로 실패하므로 저장 전에 막는다 */
    private void checkLengths(AdminPostRequest request) {
        if (request.title() != null && request.title().trim().length() > TITLE_MAX) {
            throw new BlogException(ErrorCode.POST_TITLE_TOO_LONG);
        }

        if (request.excerpt() != null && request.excerpt().length() > EXCERPT_MAX) {
            throw new BlogException(ErrorCode.POST_EXCERPT_TOO_LONG);
        }

        if (request.thumbnailImageUrl() != null && request.thumbnailImageUrl().length() > THUMBNAIL_MAX) {
            throw new BlogException(ErrorCode.POST_THUMBNAIL_TOO_LONG);
        }
    }

    /**
     * 글쓴이. 댓글이 달렸을 때 누구에게 알릴지 정하는 값이다.
     *
     * 못 찾으면 글 저장을 막지 않고 비워 둔다 — 글을 쓰는 일이 알림 때문에 실패하면 안 된다.
     */
    private Member findAuthor(Long authorId) {
        if (authorId == null) {
            return null;
        }

        return memberRepository.findById(authorId).orElse(null);
    }

    private Category findCategory(Long categoryId) {
        if (categoryId == null) {
            // category_id 가 NOT NULL 이라 임시저장에도 값이 있어야 INSERT 가 된다
            throw new BlogException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BlogException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    /**
     * 발행.
     *
     * 임시저장보다 검증이 엄격하다. 발행하면 공개 화면과 검색엔진이 그 글을 보고,
     * 요약 없이 색인되면 나중에 고쳐도 회복이 느리다. 되돌리기 비용이 비대칭이라
     * 검증도 비대칭으로 둔다.
     *
     * publishedAt 은 최초 1회만 세팅한다. 내렸다가 다시 올릴 때마다 날짜가 바뀌면
     * 독자가 보는 발행일이 계속 움직인다.
     */
    @Transactional
    public void publishPost(Long postId, LocalDateTime requestedAt) {
        Post post = findPost(postId);

        if (post.isPublished()) {
            throw new BlogException(ErrorCode.POST_ALREADY_PUBLISHED);
        }

        checkPublishable(post.getContent(), post.getExcerpt());

        LocalDateTime publishedAt = publishAt(requestedAt, post);

        // 아직 오지 않은 시각이면 예약이다. 상태를 나눠 두면 공개 조회가 시각을 따지지 않아도 된다
        if (publishedAt.isAfter(LocalDateTime.now())) {
            post.schedule(publishedAt);
            return;
        }

        post.publish(publishedAt);
    }

    /**
     * 언제 발행된 것으로 볼지 정한다.
     *
     * 값을 안 주면 지금이다. 과거 시각은 지금으로 당긴다 — 화면은 미래만 고르게 하지만
     * 브라우저 시계가 서버보다 조금 느리면 "지금"이 과거로 도착한다. 그걸 오류로 막으면
     * 아무 잘못 없는 사람이 발행을 못 한다.
     *
     * 되돌렸다 다시 발행하는 경우에는 처음 발행일을 지킨다 — 독자가 보는 날짜가 바뀌면 안 된다.
     */
    private LocalDateTime publishAt(LocalDateTime requestedAt, Post post) {
        if (requestedAt != null) {
            LocalDateTime now = LocalDateTime.now();

            return requestedAt.isBefore(now) ? now : requestedAt;
        }

        return post.getPublishedAt() == null ? LocalDateTime.now() : post.getPublishedAt();
    }

    /** 내리기. PRIVATE 이 되고 publishedAt 은 지우지 않는다 */
    @Transactional
    public void unpublishPost(Long postId) {
        Post post = findPost(postId);

        if (!post.isPublished()) {
            throw new BlogException(ErrorCode.POST_NOT_PUBLISHED_YET);
        }

        post.unpublish();
    }

    /** 삭제. 댓글을 먼저 지워야 FK 제약에 걸리지 않는다 */
    @Transactional
    public void deletePost(Long postId) {
        Post post = findPost(postId);

        commentReactionRepository.deleteByPostId(postId);
        postReactionRepository.deleteByPostId(postId);
        commentRepository.deleteByPostId(postId);
        postRepository.delete(post);
    }

    private Post findPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BlogException(ErrorCode.POST_NOT_FOUND));
    }
}
