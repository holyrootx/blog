package me.jsjlog.blog.post.domain;

import me.jsjlog.blog.common.domain.BaseEntity;

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
            String thumbnailImageUrl
    ) {
        this.title = title;
        this.excerpt = excerpt;
        this.content = content;
        this.category = category;
        this.thumbnailImageUrl = thumbnailImageUrl;
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
