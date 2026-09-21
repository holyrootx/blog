package me.jsjlog.blog.post.repository;

import java.util.List;

import me.jsjlog.blog.post.domain.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long>, CommentRepositoryCustom {

    /**
     * 글 삭제 전에 댓글을 먼저 지운다.
     * 댓글이 남아 있으면 FK 제약 때문에 글 삭제가 실패한다.
     * cascade = REMOVE 에 맡기지 않는 것은, 글을 지우면 댓글도 지워진다는 사실이
     * 코드에 드러나야 하기 때문이다.
     */
    void deleteByPostId(Long postId);

    /**
     * 내가 쓴 댓글. 설정 화면에서 쓴다.
     *
     * <p>{@code join fetch} 로 글을 같이 가져온다. 목록마다 글 제목이 필요한데 LAZY 로 두면
     * 댓글 수만큼 조회가 더 나간다.</p>
     *
     * <p>최신순이다. 번호 역순으로 정렬하는 것은 작성 시각이 같은 초에 여러 건 들어와도
     * 순서가 흔들리지 않게 하기 위해서다.</p>
     */
    @Query("select c from Comment c join fetch c.post where c.member.id = :memberId order by c.id desc")
    List<Comment> findMyComments(@Param("memberId") Long memberId, Pageable pageable);
}
