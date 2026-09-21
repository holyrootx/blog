package me.jsjlog.blog.post.domain;

import me.jsjlog.blog.common.domain.BaseEntity;
import me.jsjlog.blog.member.domain.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "post")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "excerpt", length = 500)
    private String excerpt; // a short piece of text

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /**
     * 글쓴이.
     *
     * <p>댓글이 달렸을 때 누구에게 알릴지 정하려고 둔다. 없이도 {@code role = ADMIN} 조회로
     * 우회할 수 있지만, 그러면 "필자는 관리자 한 명" 이라는 전제가 코드에 박힌다.</p>
     *
     * <p>널을 허용한다. 이 컬럼이 생기기 전에 쓴 글에는 값이 없고, MySQL 은 행이 있는 테이블에
     * NOT NULL 컬럼을 그냥 붙이지 못한다. 값이 없으면 알림을 보내지 않는다.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Member author;

    @Column(name = "thumbnail_image_url", length = 500)
    private String thumbnailImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PostStatus status;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "views", nullable = false)
    private long views;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    public Post(
            String title,
            String excerpt,
            String content,
            Category category,
            String thumbnailImageUrl,
            Member author
    ) {
        this.title = title;
        this.excerpt = excerpt;
        this.content = content;
        this.category = category;
        this.thumbnailImageUrl = thumbnailImageUrl;
        this.author = author;
        this.status = PostStatus.DRAFT;
        this.views = 0;
        this.likeCount = 0;
    }

    public void update(
            String title,
            String excerpt,
            String content,
            Category category,
            String thumbnailImageUrl
    ) {
        this.title = title;
        this.excerpt = excerpt;
        this.content = content;
        this.category = category;
        this.thumbnailImageUrl = thumbnailImageUrl;
    }

    /**
     * 글을 공개 발행합니다.
     *
     * <p>발행 시각을 파라미터로 받는 이유는 엔티티가 시간 소스에 직접 의존하지 않게 하려는 것입니다.
     * 서비스 계층이 시각을 정합니다.</p>
     */
    public void publish(LocalDateTime publishedAt) {
        this.status = PostStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }

    /**
     * 미래 시각으로 발행을 예약합니다. 그 시각이 오기 전까지 공개 조회에 잡히지 않습니다.
     *
     * <p>시각 비교는 서비스가 합니다. 엔티티가 현재 시각을 직접 읽으면 테스트에서 시간을 다룰 수 없습니다.</p>
     */
    public void schedule(LocalDateTime publishedAt) {
        this.status = PostStatus.SCHEDULED;
        this.publishedAt = publishedAt;
    }

    /**
     * 발행한 글을 비공개로 되돌립니다.
     *
     * <p>{@code DRAFT}가 아니라 {@code PRIVATE}가 되는 이유는, 한 번도 발행하지 않은 글과
     * 발행했다가 내린 글이 관리자 화면에서 서로 다른 탭이기 때문입니다.</p>
     *
     * <p>{@code publishedAt}은 지우지 않습니다. 다시 발행할 때 최초 발행일을 유지하는 것이
     * 독자에게 자연스럽고, 되돌린 뒤 재발행할 때마다 날짜가 바뀌는 것을 막습니다.</p>
     */
    public void unpublish() {
        this.status = PostStatus.PRIVATE;
    }

    /**
     * 카테고리를 옮깁니다. 카테고리 삭제 시 미분류로 이동시킬 때 사용합니다.
     */
    public void moveTo(Category category) {
        this.category = category;
    }

    public boolean isPublished() {
        return this.status == PostStatus.PUBLISHED;
    }
}
