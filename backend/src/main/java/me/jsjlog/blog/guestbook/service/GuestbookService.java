package me.jsjlog.blog.guestbook.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.guestbook.domain.GuestbookEntry;
import me.jsjlog.blog.guestbook.domain.GuestbookReport;
import me.jsjlog.blog.guestbook.dto.GuestbookEntryResponse;
import me.jsjlog.blog.guestbook.dto.GuestbookPageResponse;
import me.jsjlog.blog.guestbook.dto.GuestbookWriteRequest;
import me.jsjlog.blog.guestbook.repository.GuestbookEntryRepository;
import me.jsjlog.blog.guestbook.repository.GuestbookReportRepository;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.notification.service.NotificationService;
import me.jsjlog.blog.post.domain.CommentReportReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 공개 방명록. 쓰기·고치기·지우기·신고는 로그인한 회원만 한다.
 *
 * <p>익명으로 열어 두면 도배를 막을 기준이 IP 뿐인데, 이 서버는 클라이언트 IP 헤더를 믿지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
public class GuestbookService {

	private static final int DEFAULT_SIZE = 10;
	private static final int MAX_SIZE = 50;

	private final GuestbookEntryRepository entryRepository;
	private final GuestbookReportRepository reportRepository;
	private final MemberRepository memberRepository;
	private final NotificationService notificationService;
	private final GuestbookWriteGuard writeGuard;

	/**
	 * 공개 목록 한 쪽.
	 *
	 * <p>{@code entryId} 를 주면 그 글이 있는 쪽을 준다. 알림을 눌러 온 사람이 오래된 글을 찾으러
	 * 쪽을 넘기지 않게 한다. 볼 수 없는 글이면(지움·가림) 첫 쪽을 준다.</p>
	 */
	@Transactional(readOnly = true)
	public GuestbookPageResponse getEntries(
			Integer page,
			Integer size,
			Long entryId,
			Long viewerId,
			boolean viewerIsAdmin
	) {
		int pageNumber = page == null || page < 0 ? 0 : page;
		int pageSize = size == null || size < 1 ? DEFAULT_SIZE : size;

		if (pageSize > MAX_SIZE) {
			throw new BlogException(ErrorCode.GUESTBOOK_SIZE_LIMIT_EXCEEDED);
		}

		if (entryId != null) {
			pageNumber = entryRepository.isVisible(viewerId, entryId)
					? (int) (entryRepository.countVisibleNewerThan(viewerId, entryId) / pageSize)
					: 0;
		}

		Page<GuestbookEntry> entries = entryRepository.findVisible(viewerId, PageRequest.of(pageNumber, pageSize));
		Set<Long> reported = reportedByViewer(entries.getContent(), viewerId);

		return new GuestbookPageResponse(
				entries.getContent().stream()
						.map(entry -> GuestbookEntryResponse.of(
								entry,
								viewerId,
								viewerIsAdmin,
								reported.contains(entry.getId())
						))
						.toList(),
				pageNumber,
				pageSize,
				entries.getTotalElements(),
				entries.getTotalPages(),
				GuestbookEntry.MAX_CONTENT_LENGTH
		);
	}

	@Transactional
	public Long write(GuestbookWriteRequest request, Long memberId) {
		String content = requireContent(request == null ? null : request.content());
		Member member = findActiveMember(memberId);
		// 실패한 시도도 횟수에 넣는다. 거절당한 요청을 무한히 다시 보내는 것도 도배다
		writeGuard.check(memberId);

		GuestbookEntry entry = entryRepository.save(new GuestbookEntry(
				member,
				content,
				request.secretOrDefault()
		));

		notificationService.notifyGuestbookWritten(entry);

		return entry.getId();
	}

	@Transactional
	public void update(Long entryId, GuestbookWriteRequest request, Long memberId) {
		String content = requireContent(request == null ? null : request.content());
		GuestbookEntry entry = findMyUsableEntry(entryId, memberId);
		entry.update(content, request.secretOrDefault());
	}

	/**
	 * 내 글 지우기. 행은 남기고 공개 화면에서 내린다.
	 *
	 * <p>신고 처리와 분쟁 대응을 위해 지운 원문은 보관 기간 동안 운영자만 본다. 기간이 지나면
	 * {@link GuestbookRetention} 이 비운다.</p>
	 */
	@Transactional
	public void delete(Long entryId, Long memberId) {
		findMyUsableEntry(entryId, memberId).delete(LocalDateTime.now());
	}

	/**
	 * 신고. 접수만 하고 저절로 가리지 않는다 — 몰려서 신고하면 멀쩡한 글이 사라진다.
	 * 사람이 보도록 운영자에게 알린다.
	 */
	@Transactional
	public void report(Long entryId, CommentReportReason reason, String detail, Long memberId) {
		if (reason == null) {
			throw new BlogException(ErrorCode.COMMENT_REPORT_REASON_REQUIRED);
		}

		Member member = findActiveMember(memberId);
		GuestbookEntry entry = entryRepository.findLockedById(entryId)
				.orElseThrow(() -> new BlogException(ErrorCode.GUESTBOOK_NOT_FOUND));

		if (entry.isDeleted()) {
			throw new BlogException(ErrorCode.GUESTBOOK_DELETED);
		}

		// 남의 비밀글은 목록에서 본문이 안 보인다. 읽지 못한 글을 신고할 수는 없고,
		// 번호만으로 받아 주면 비밀글이 있다는 것을 확인하는 길이 된다
		if (entry.isSecret() && !entry.isWrittenBy(memberId) && !member.isAdmin()) {
			throw new BlogException(ErrorCode.GUESTBOOK_NOT_FOUND);
		}

		if (entry.isWrittenBy(memberId)) {
			throw new BlogException(ErrorCode.GUESTBOOK_REPORT_SELF);
		}

		if (reportRepository.existsByEntryIdAndMemberId(entryId, memberId)) {
			throw new BlogException(ErrorCode.GUESTBOOK_REPORT_DUPLICATED);
		}

		reportRepository.save(new GuestbookReport(entry, member, reason, detail));
		notificationService.notifyGuestbookReported(entry);
	}

	private Set<Long> reportedByViewer(List<GuestbookEntry> entries, Long viewerId) {
		if (viewerId == null || entries.isEmpty()) {
			return Set.of();
		}

		return new HashSet<>(reportRepository.findReportedEntryIds(
				viewerId,
				entries.stream().map(GuestbookEntry::getId).toList()
		));
	}

	private GuestbookEntry findMyUsableEntry(Long entryId, Long memberId) {
		Member member = findActiveMember(memberId);
		GuestbookEntry entry = entryRepository.findLockedById(entryId)
				.orElseThrow(() -> new BlogException(ErrorCode.GUESTBOOK_NOT_FOUND));

		// 남의 비밀글은 있는지조차 알려 주지 않는다
		if (!entry.isWrittenBy(member.getId())) {
			throw new BlogException(entry.isSecret()
					? ErrorCode.GUESTBOOK_NOT_FOUND
					: ErrorCode.GUESTBOOK_NOT_MINE);
		}

		// 가린 것과 지운 것에 다른 말을 준다. 가려진 사람이 자기가 지운 줄 알면 영문을 모른다
		if (entry.isDeleted()) {
			throw new BlogException(entry.isHiddenByAdmin()
					? ErrorCode.GUESTBOOK_HIDDEN_BY_ADMIN
					: ErrorCode.GUESTBOOK_DELETED);
		}

		return entry;
	}

	private Member findActiveMember(Long memberId) {
		if (memberId == null) {
			throw new BlogException(ErrorCode.UNAUTHORIZED);
		}

		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BlogException(ErrorCode.UNAUTHORIZED));

		if (!member.getStatusCode().isActive()) {
			throw new BlogException(ErrorCode.FORBIDDEN);
		}

		return member;
	}

	private String requireContent(String content) {
		if (!StringUtils.hasText(content)) {
			throw new BlogException(ErrorCode.GUESTBOOK_CONTENT_REQUIRED);
		}

		String trimmed = content.trim();
		if (trimmed.length() > GuestbookEntry.MAX_CONTENT_LENGTH) {
			throw new BlogException(ErrorCode.GUESTBOOK_CONTENT_TOO_LONG);
		}

		return trimmed;
	}
}
