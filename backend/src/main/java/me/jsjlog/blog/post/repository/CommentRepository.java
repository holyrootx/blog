package me.jsjlog.blog.post.repository;

import me.jsjlog.blog.post.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long>, CommentRepositoryCustom {

    /**
     * 글 삭제 전에 댓글을 먼저 지운다.
     * 댓글이 남아 있으면 FK 제약 때문에 글 삭제가 실패한다.
     * cascade = REMOVE 에 맡기지 않는 것은, 글을 지우면 댓글도 지워진다는 사실이
     * 코드에 드러나야 하기 때문이다.
     */
    void deleteByPostId(Long postId);
}
