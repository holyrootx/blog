package me.jsjlog.blog.guestbook.repository;

import java.util.Collection;
import java.util.List;

import me.jsjlog.blog.guestbook.domain.GuestbookReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuestbookReportRepository extends JpaRepository<GuestbookReport, Long> {

    boolean existsByEntryIdAndMemberId(Long entryId, Long memberId);

    /** 목록에서 "이미 신고함" 을 표시할 글. 한 화면 분량을 한 번에 본다 */
    @Query("select r.entry.id from GuestbookReport r where r.member.id = :memberId and r.entry.id in :entryIds")
    List<Long> findReportedEntryIds(
            @Param("memberId") Long memberId,
            @Param("entryIds") Collection<Long> entryIds
    );

    List<GuestbookReport> findByEntryIdOrderByIdAsc(Long entryId);

    List<GuestbookReport> findByEntryIdAndHandledAtIsNull(Long entryId);

    /** 원문을 파기할 때 신고 당시 사본도 같이 지운다. 사본만 남으면 파기한 것이 아니다 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update GuestbookReport r set r.reportedContent = null where r.entry.id in :entryIds")
    int clearReportedContent(@Param("entryIds") Collection<Long> entryIds);
}
