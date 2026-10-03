package me.jsjlog.blog.post.repository;

import java.util.List;
import java.util.Optional;

import me.jsjlog.blog.post.domain.CommentReaction;
import me.jsjlog.blog.post.domain.CommentReactionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentReactionRepository extends JpaRepository<CommentReaction, Long> {

    Optional<CommentReaction> findByCommentIdAndMemberIdAndType(
            Long commentId,
            Long memberId,
            CommentReactionType type
    );

    List<CommentReaction> findAllByCommentIdAndMemberId(Long commentId, Long memberId);

    long countByCommentIdAndType(Long commentId, CommentReactionType type);

}
