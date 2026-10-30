package me.jsjlog.blog.guestbook.service;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.guestbook.repository.GuestbookEntryRepository;
import me.jsjlog.blog.guestbook.repository.GuestbookReportRepository;
import me.jsjlog.blog.post.service.CommentRetention;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지운 방명록 원문 정리. 하루 한 번 돈다.
 *
 * <p>쓴 사람이 지운 날부터 댓글과 같은 기간({@link CommentRetention#RETENTION})이 지나면 본문, 운영자 답글,
 * 신고 당시 사본을 비운다. 행은 남긴다 — 신고·처리 기록이 어느 글 것인지 남아야 한다.</p>
 *
 * <p>운영자가 가리기만 한 것은 지운 시각이 없어서 정리하지 않는다(2026-10-18 결정 — 가린 동안 보관).
 * 쓴 사람이 지운 뒤 운영자가 가린 것은 지운 시각부터 세어 정리한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestbookRetention {

	private final GuestbookEntryRepository entryRepository;
	private final GuestbookReportRepository reportRepository;

	// 댓글 정리와 같은 시각에 돈다. 손님이 적은 새벽이다
	@Transactional
	@Scheduled(cron = "${blog.retention.purge-cron:0 30 4 * * *}", zone = "Asia/Seoul")
	public void purgeExpired() {
		purgeExpired(LocalDateTime.now());
	}

	@Transactional
	public int purgeExpired(LocalDateTime now) {
		LocalDateTime cutoff = now.minus(CommentRetention.RETENTION);
		List<Long> candidates = entryRepository.findExpiredDeletedIds(cutoff);

		if (candidates.isEmpty()) {
			return 0;
		}

		entryRepository.purgeContent(candidates, cutoff, now);
		List<Long> purgedIds = entryRepository.findPurgedIn(candidates);

		if (!purgedIds.isEmpty()) {
			reportRepository.clearReportedContent(purgedIds);
			log.info("[보관 정리] 지운 방명록 원문 {}건 파기", purgedIds.size());
		}

		return purgedIds.size();
	}
}
