package me.jsjlog.blog.admin.service;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminGuestbookDetail;
import me.jsjlog.blog.admin.dto.AdminGuestbookListResponse;
import me.jsjlog.blog.admin.dto.AdminGuestbookReportResponse;
import me.jsjlog.blog.admin.dto.AdminGuestbookSearchCondition;
import me.jsjlog.blog.admin.repository.AdminGuestbookQueryRepository;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.guestbook.domain.GuestbookEntry;
import me.jsjlog.blog.guestbook.domain.GuestbookReport;
import me.jsjlog.blog.guestbook.repository.GuestbookEntryRepository;
import me.jsjlog.blog.guestbook.repository.GuestbookReportRepository;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberRole;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.notification.service.NotificationService;
import me.jsjlog.blog.post.service.CommentRetention;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 관리자 방명록. 답글, 가리기·되돌리기, 신고 판단.
 *
 * <p>남의 글을 고치는 기능은 없다. 남이 한 말을 바꿔 놓는 것은 가리는 것과 다른 일이다.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminGuestbookService {

    private final AdminGuestbookQueryRepository queryRepository;
    private final GuestbookEntryRepository entryRepository;
    private final GuestbookReportRepository reportRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public AdminGuestbookListResponse getEntries(AdminGuestbookSearchCondition condition) {
        long totalElements = queryRepository.countEntries(condition);
        int size = condition.sizeOrDefault();

        return new AdminGuestbookListResponse(
                queryRepository.getEntries(condition),
                queryRepository.countByStatus(condition),
                condition.pageOrDefault(),
                size,
                totalElements,
                (int) Math.ceil((double) totalElements / size)
        );
    }

    @Transactional(readOnly = true)
    public AdminGuestbookDetail getDetail(Long entryId) {
        GuestbookEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new BlogException(ErrorCode.GUESTBOOK_NOT_FOUND));

        List<AdminGuestbookReportResponse> reports = reportRepository.findByEntryIdOrderByIdAsc(entryId)
                .stream()
                .map(AdminGuestbookReportResponse::from)
                .toList();

        return new AdminGuestbookDetail(
                queryRepository.toSummary(entry),
                entry.getDeletedAt(),
                retainedUntil(entry),
                reports
        );
    }

    /** 답글을 달거나 고친다. 한 글에 하나뿐이라 같은 자리로 덮어쓴다 */
    @Transactional
    public void reply(Long entryId, String content, Long adminId) {
        Member admin = findActiveAdmin(adminId);
        String text = requireReply(content);
        GuestbookEntry entry = lockEntry(entryId);

        // 지우거나 가린 글에 답하면 그 답글은 아무도 못 본다
        if (entry.isDeleted()) {
            throw new BlogException(ErrorCode.GUESTBOOK_DELETED);
        }

        boolean firstReply = !entry.hasReply();
        entry.reply(admin, text, LocalDateTime.now());

        // 고칠 때는 알리지 않는다. 지웠다가 새로 달면 새 답이라 다시 알린다
        if (firstReply) {
            notificationService.notifyGuestbookReplied(entry);
        }
    }

    @Transactional
    public void removeReply(Long entryId, Long adminId) {
        findActiveAdmin(adminId);
        lockEntry(entryId).removeReply();
    }

    /**
     * 가리거나 되돌린다. 어느 쪽이든 운영자가 이 글을 본 것이므로 걸린 신고를 같이 처리한다 —
     * 두 번 눌러야 하면 한쪽을 빠뜨리고 신고 목록에 계속 남는다.
     */
    @Transactional
    public void updateVisibility(Long entryId, Boolean hidden, Long adminId) {
        if (hidden == null) {
            throw new BlogException(ErrorCode.GUESTBOOK_VISIBILITY_REQUIRED);
        }

        Member admin = findActiveAdmin(adminId);
        GuestbookEntry entry = lockEntry(entryId);

        // 쓴 사람이 지운 글은 되살리지 않는다. 운영자는 자기가 가린 글만 되돌린다
        if (!hidden && entry.isDeletedByWriter()) {
            throw new BlogException(ErrorCode.GUESTBOOK_DELETED_BY_WRITER);
        }

        if (hidden) {
            // 보이던 글을 가릴 때만 알린다. 쓴 사람이 이미 지운 글이나 이미 가린 글을 다시 가리는 것은
            // 쓴 사람에게 새로 일어난 일이 아니다
            boolean wasVisible = !entry.isDeleted();
            entry.hideByAdmin();

            if (wasVisible) {
                notificationService.notifyGuestbookHidden(entry, admin);
            }
        } else {
            entry.restore();
        }

        markReportsHandled(entryId);
    }

    /** 신고를 봤지만 글은 그대로 둔다. 이게 없으면 "문제 없음" 을 남길 방법이 없다 */
    @Transactional
    public void dismissReports(Long entryId, Long adminId) {
        findActiveAdmin(adminId);
        lockEntry(entryId);
        markReportsHandled(entryId);
    }

    private void markReportsHandled(Long entryId) {
        LocalDateTime now = LocalDateTime.now();

        for (GuestbookReport report : reportRepository.findByEntryIdAndHandledAtIsNull(entryId)) {
            report.markHandled(now);
        }
    }

    /** 쓴 사람이 지운 글의 원문 파기 예정일. 지운 뒤 운영자가 가렸어도 지운 시각부터 센다 */
    private LocalDateTime retainedUntil(GuestbookEntry entry) {
        if (entry.isContentPurged() || entry.getDeletedAt() == null) {
            return null;
        }

        return entry.getDeletedAt().plus(CommentRetention.RETENTION);
    }

    private GuestbookEntry lockEntry(Long entryId) {
        GuestbookEntry entry = entryRepository.findLockedById(entryId)
                .orElseThrow(() -> new BlogException(ErrorCode.GUESTBOOK_NOT_FOUND));

        // 파기한 원문은 돌아오지 않는다. 빈 본문으로 다시 공개되거나 답글이 붙지 않게 한다
        if (entry.isContentPurged()) {
            throw new BlogException(ErrorCode.GUESTBOOK_CONTENT_PURGED);
        }

        return entry;
    }

    private Member findActiveAdmin(Long memberId) {
        if (memberId == null) {
            throw new BlogException(ErrorCode.UNAUTHORIZED);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BlogException(ErrorCode.UNAUTHORIZED));

        if (!member.getStatusCode().isActive() || member.getRole() != MemberRole.ADMIN) {
            throw new BlogException(ErrorCode.FORBIDDEN);
        }

        return member;
    }

    private String requireReply(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BlogException(ErrorCode.GUESTBOOK_REPLY_REQUIRED);
        }

        String trimmed = content.trim();
        if (trimmed.length() > GuestbookEntry.MAX_CONTENT_LENGTH) {
            throw new BlogException(ErrorCode.GUESTBOOK_REPLY_TOO_LONG);
        }

        return trimmed;
    }
}
