package me.jsjlog.blog.post.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.jsjlog.blog.common.domain.BaseEntity;
import me.jsjlog.blog.member.domain.Member;

/**
 * 게시글 반응.
 *
 * <p>종류를 행에 함께 저장한다. 화면에는 우선 좋아요만 보여도, 나중에 싫어요를 추가할 때
 * 테이블을 다시 바꾸지 않아도 된다. 한 회원이 같은 종류를 두 번 누르는 것만 DB에서 막는다.</p>
 */
@Getter
@Entity
@Table(
        name = "post_reaction",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_post_reaction_post_member_type",
                columnNames = {"post_id", "member_id", "reaction_type"}
        ),
        indexes = {
                @Index(name = "idx_post_reaction_member", columnList = "member_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostReaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 20)
    private PostReactionType type;

    public PostReaction(Post post, Member member, PostReactionType type) {
        this.post = post;
        this.member = member;
        this.type = type;
    }
}
