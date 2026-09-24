package me.jsjlog.blog.post.repository;

import java.util.List;
import java.util.Optional;

import me.jsjlog.blog.post.domain.PostReaction;
import me.jsjlog.blog.post.domain.PostReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostReactionRepository extends JpaRepository<PostReaction, Long> {

    Optional<PostReaction> findByPostIdAndMemberIdAndType(
            Long postId,
            Long memberId,
            PostReactionType type
    );

    List<PostReaction> findAllByPostIdAndMemberId(Long postId, Long memberId);

    long countByPostIdAndType(Long postId, PostReactionType type);

    @Modifying
    @Query("delete from PostReaction reaction where reaction.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
