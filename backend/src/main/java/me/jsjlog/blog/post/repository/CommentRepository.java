package me.jsjlog.blog.post.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import me.jsjlog.blog.post.domain.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long>, CommentRepositoryCustom {

    @Query("select c.post.id from Comment c where c.id = :commentId")
    Optional<Long> findPostId(@Param("commentId") Long commentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Comment c where c.id = :commentId")
    Optional<Comment> findLockedById(@Param("commentId") Long commentId);

    /**
     * 내가 쓴 댓글. 설정 화면에서 쓴다.
     *
     * <p>{@code join fetch} 로 글을 같이 가져온다. 목록마다 글 제목이 필요한데 LAZY 로 두면
     * 댓글 수만큼 조회가 더 나간다.</p>
     *
     * <p>최신순이다. 번호 역순으로 정렬하는 것은 작성 시각이 같은 초에 여러 건 들어와도
     * 순서가 흔들리지 않게 하기 위해서다.</p>
     */
    @Query("select c from Comment c join fetch c.post p where c.member.id = :memberId"
            + " and p.deletedAt is null and p.status = me.jsjlog.blog.post.domain.PostStatus.PUBLISHED"
            + " order by c.id desc")
    List<Comment> findMyComments(@Param("memberId") Long memberId, Pageable pageable);

    /** 댓글 삭제 또는 원글 영구 삭제 중 먼저 도래한 기한으로 원문을 파기한다. */
    @Query("select c.id from Comment c where c.contentPurgedAt is null"
            + " and ((c.deleted = true and c.deletedAt < :cutoff)"
            + " or c.post.id in (select p.id from Post p where p.contentPurgedAt < :cutoff))")
    List<Long> findExpiredDeletedIds(@Param("cutoff") LocalDateTime cutoff);

    /**
     * 지운 원문을 비운다.
     *
     * 고른 뒤에 조건을 UPDATE 에 한 번 더 건다. 운영자가 같은 댓글을 되살리는 중이면 행 잠금이
     * 풀린 뒤 지금 상태로 다시 따지므로, 되살린 댓글은 건드리지 않는다. 엔티티를 고쳐 저장하면
     * 잠금을 기다리는 사이 읽은 옛 값으로 되살린 결과를 덮어쓸 수 있다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Comment c set c.content = '', c.contentPurgedAt = :now, c.deleted = true"
            + " where c.id in :ids and c.contentPurgedAt is null"
            + " and ((c.deleted = true and c.deletedAt < :cutoff)"
            + " or c.post.id in (select p.id from Post p where p.contentPurgedAt < :cutoff))")
    int purgeContent(
            @Param("ids") Collection<Long> ids,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now
    );

    /**
     * 후보 중 파기된 것. 시각을 맞춰 보지 않는다 — DB 가 소수점 아래를 잘라 저장하면 값이 어긋난다.
     * 같은 때 다른 정리가 먼저 비운 것도 섞이는데, 그 기록도 지울 대상이라 상관없다.
     */
    @Query("select c.id from Comment c where c.id in :ids and c.contentPurgedAt is not null")
    List<Long> findPurgedIn(@Param("ids") Collection<Long> ids);
}
