package me.jsjlog.blog.post.domain;

import me.jsjlog.blog.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Entity
@Table(name = "comment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    /**
     * 부모 댓글. {@code null}이면 최상위 댓글, 값이 있으면 답글입니다.
     *
     * <p>역방향 컬렉션({@code replies})은 두지 않습니다. 순환참조와 컬렉션 상태 관리를
     * 피하려는 것이며, 답글은 {@code CommentRepository}에서 {@code parentId}로 직접 조회합니다.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Column(name = "guest_nickname", nullable = false, length = 50)
    private String guestNickname;

    /**
     * 게스트 본인 확인용 BCrypt 해시입니다. 평문은 어디에도 저장하지 않습니다.
     *
     * <p>관리자가 작성한 댓글({@code authorComment = true})은 인증으로 권한이 확인되므로
     * 비밀번호가 없습니다. 그래서 {@code null}을 허용합니다. 더미 해시를 채워 넣으면
     * "비밀번호가 있는 댓글"과 "없는 댓글"을 구분할 수 없어집니다.</p>
     *
     * <p>이 값이 {@code null}인 댓글은 게스트가 수정·삭제할 수 없습니다. 서비스에서 거부합니다.</p>
     */
    @Column(name = "guest_password_hash", length = 255)
    private String guestPasswordHash;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 비밀 댓글. 공개 API는 이 값이 true면 본문을 응답에 담지 않습니다. */
    @Column(name = "secret", nullable = false)
    private boolean secret;

    /** 블로그 주인이 작성한 댓글. 관리자 경로에서만 true로 설정합니다. */
    @Column(name = "author_comment", nullable = false)
    private boolean authorComment;

    /**
     * soft delete 표식입니다.
     *
     * <p>답글이 달린 댓글을 물리 삭제하면 답글이 부모를 잃기 때문에 레코드를 남깁니다.
     * 화면에는 "삭제된 댓글입니다"로 표시하고 답글은 그대로 보여줍니다.</p>
     */
    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    public Comment(
            Post post,
            Comment parent,
            String guestNickname,
            String guestPasswordHash,
            String content,
            boolean secret,
            boolean authorComment
    ) {
        this.post = post;
        this.parent = parent;
        this.guestNickname = guestNickname;
        this.guestPasswordHash = guestPasswordHash;
        this.content = content;
        this.secret = secret;
        this.authorComment = authorComment;
        this.deleted = false;
        this.likeCount = 0;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void delete() {
        this.deleted = true;
    }

    /**
     * 답글 여부입니다. 답글에 답글을 다는 것을 막는 검증에서 사용합니다.
     */
    public boolean isReply() {
        return this.parent != null;
    }
}
