package me.jsjlog.blog.post.domain;

import me.jsjlog.blog.common.domain.BaseEntity;
import me.jsjlog.blog.member.domain.Member;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * soft delete 표식입니다.
     *
     * <p>답글이 달린 댓글을 물리 삭제하면 답글이 부모를 잃기 때문에 레코드를 남깁니다.
     * 화면에는 "삭제된 댓글입니다"로 표시하고 답글은 그대로 보여줍니다.</p>
     */
    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    /**
     * 글쓴이가 내용을 고쳤는가.
     *
     * <p>{@code updatedAt} 으로 판단하지 않는다. 그 값은 관리자가 숨기거나 되살릴 때도,
     * 지울 때도 같이 바뀌어서, 손대지 않은 댓글에 "수정됨" 이 붙는다.</p>
     *
     * <p>표시를 남기는 이유는 읽는 사람 때문이다. 말이 오간 뒤에 조용히 바꿔 놓으면
     * 뒤에 달린 답글이 엉뚱한 말에 답한 것처럼 보인다.</p>
     */
    @Column(name = "edited", nullable = false)
    private boolean edited;

    /**
     * 안 보이게 된 까닭이 운영자인가.
     *
     * <p>{@code deleted} 는 "안 보인다" 까지만 말한다. 글쓴이가 지운 것과 운영자가 가린 것이
     * 같은 칸을 쓰면, 가려진 사람은 자기가 지운 줄 알고 운영자는 회원이 지운 것을 자기가
     * 가린 것으로 본다. 그래서 까닭을 따로 남긴다.</p>
     *
     * <p>거르는 조건은 여전히 {@code deleted} 하나로 본다 — 어느 쪽이든 안 보이는 것은
     * 같아서, 목록·답글·알림은 까닭을 알 필요가 없다. 이 값은 화면에 뭐라고 쓸지에만 쓴다.</p>
     *
     * <p>{@code deleted} 가 거짓이면 이 값은 뜻이 없다.</p>
     */
    @Column(name = "hidden_by_admin", nullable = false)
    private boolean hiddenByAdmin;

    public Comment(
            Post post,
            Comment parent,
            Member member,
            String content
    ) {
        this.post = post;
        this.parent = parent;
        this.member = member;
        this.content = content;
        this.deleted = false;
        this.edited = false;
        this.hiddenByAdmin = false;
    }

    /**
     * 글쓴이가 내용을 고친다.
     *
     * <p>관리자가 부르는 자리가 아니다. 남이 한 말을 다른 말로 바꿔 놓는 것은 숨기는 것과
     * 다른 일이라, 관리자에게는 숨기기만 있다.</p>
     */
    public void updateContent(String content) {
        this.content = content;
        this.edited = true;
    }

    /** 글쓴이가 지운다 */
    public void delete() {
        this.deleted = true;
        this.hiddenByAdmin = false;
    }

    /** 운영자가 가린다. 글쓴이가 지운 것과 화면에 다르게 나온다 */
    public void hideByAdmin() {
        this.deleted = true;
        this.hiddenByAdmin = true;
    }

    /** 운영자가 되돌린다. 글쓴이가 지운 글까지 되살릴 수 있는 것은 의도한 바다 —
        잘못 지웠다는 요청이 올 수 있고, 되돌린 사실은 조치 이력에 남는다 */
    public void restore() {
        this.deleted = false;
        this.hiddenByAdmin = false;
    }

    /**
     * 답글 여부입니다. 답글에 답글을 다는 것을 막는 검증에서 사용합니다.
     */
    public boolean isReply() {
        return this.parent != null;
    }
}
