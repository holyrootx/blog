package me.jsjlog.blog.guestbook.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import me.jsjlog.blog.guestbook.domain.GuestbookEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuestbookEntryRepository extends JpaRepository<GuestbookEntry, Long> {

    /**
     * 고치기·지우기·가리기·답글은 모두 이 잠금을 거친다.
     *
     * <p>엔티티 저장은 모든 칸을 다시 쓴다. 잠그지 않으면 쓴 사람이 고치는 사이 운영자가 가린 것이
     * 고친 저장에 덮여 다시 보이게 된다.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from GuestbookEntry e where e.id = :id")
    Optional<GuestbookEntry> findLockedById(@Param("id") Long id);

    /**
     * 공개 목록. 최신순이다.
     *
     * <p>지운 것과 가린 것은 빠진다. 다만 <b>운영자가 가린 내 글은 나에게만 남긴다</b> — 흔적 없이
     * 사라지면 안 써진 줄 알고 다시 쓰고, 또 가려진다.</p>
     */
    @Query(value = "select e from GuestbookEntry e join fetch e.member left join fetch e.replyMember"
            + " where e.deleted = false"
            + " or (e.hiddenByAdmin = true and e.contentPurgedAt is null and e.member.id = :viewerId)"
            + " order by e.id desc",
            countQuery = "select count(e) from GuestbookEntry e where e.deleted = false"
                    + " or (e.hiddenByAdmin = true and e.contentPurgedAt is null and e.member.id = :viewerId)")
    Page<GuestbookEntry> findVisible(@Param("viewerId") Long viewerId, Pageable pageable);

    /**
     * 공개 목록에서 이 글보다 위(최신)에 있는 글 수. 알림으로 찾아온 글이 몇 쪽에 있는지 셀 때 쓴다.
     * 조건은 {@link #findVisible} 과 같아야 쪽 번호가 맞는다.
     */
    @Query("select count(e) from GuestbookEntry e where e.id > :entryId"
            + " and (e.deleted = false"
            + " or (e.hiddenByAdmin = true and e.contentPurgedAt is null and e.member.id = :viewerId))")
    long countVisibleNewerThan(@Param("viewerId") Long viewerId, @Param("entryId") Long entryId);

    @Query("select count(e) > 0 from GuestbookEntry e where e.id = :entryId"
            + " and (e.deleted = false"
            + " or (e.hiddenByAdmin = true and e.contentPurgedAt is null and e.member.id = :viewerId))")
    boolean isVisible(@Param("viewerId") Long viewerId, @Param("entryId") Long entryId);

    /** 쓴 사람이 지운 지 보관 기간이 지난 것. 운영자가 가린 것은 기한이 없다 */
    @Query("select e.id from GuestbookEntry e where e.contentPurgedAt is null"
            + " and e.deleted = true and e.hiddenByAdmin = false and e.deletedAt < :cutoff")
    List<Long> findExpiredDeletedIds(@Param("cutoff") LocalDateTime cutoff);

    /**
     * 지운 원문과 답글을 비운다.
     *
     * <p>고른 뒤에 조건을 한 번 더 건다. 운영자가 같은 글을 되살리는 중이면 잠금이 풀린 뒤
     * 지금 상태로 다시 따지므로 되살린 글은 건드리지 않는다.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update GuestbookEntry e set e.content = '', e.replyContent = null, e.replyMember = null,"
            + " e.repliedAt = null, e.contentPurgedAt = :now"
            + " where e.id in :ids and e.contentPurgedAt is null"
            + " and e.deleted = true and e.hiddenByAdmin = false and e.deletedAt < :cutoff")
    int purgeContent(
            @Param("ids") Collection<Long> ids,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now
    );

    @Query("select e.id from GuestbookEntry e where e.id in :ids and e.contentPurgedAt is not null")
    List<Long> findPurgedIn(@Param("ids") Collection<Long> ids);
}
