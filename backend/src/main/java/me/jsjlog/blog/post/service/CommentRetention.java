package me.jsjlog.blog.post.service;

import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.history.domain.ContentHistory.Target;
import me.jsjlog.blog.history.repository.ContentHistoryRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 보관 기간 정리. 하루 한 번 돈다.
 *
 * 같은 기준 시각으로 두 가지를 한다.
 * 1. 글쓴이가 지운 댓글의 원문: 지운 시각부터 6개월이 지나면 비우고, 그 댓글의 변경 기록도 함께 지운다.
 * 2. 변경 기록: 기록된 시각부터 6개월이 지나면 지운다.
 *
 * 원문만 비우고 댓글 레코드는 남긴다. 답글이 부모를 잃지 않고, 신고·조치 기록도 어느 댓글
 * 것인지 남는다.
 *
 * 1에서 기록까지 같이 지우는 이유가 있다. 지운 뒤에 운영자가 가린 기록은 지운 시각보다 늦게
 * 생겨서, 기록 시각만 보고 지우면 원문 사본이 그만큼 더 남는다.
 *
 * 6개월은 개인정보 처리방침에 적은 서비스 기준이다. 법이 정한 기간이 아니다.
 * 기준 시각보다 앞선 것만 지운다. 정확히 6개월 된 것은 다음 날 정리 때 지운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentRetention {

    public static final Period RETENTION = Period.ofMonths(6);

    private final CommentRepository commentRepository;
    private final ContentHistoryRepository historyRepository;

    // 손님이 적은 새벽에 돈다
    @Transactional
    @Scheduled(cron = "${blog.retention.purge-cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void purgeExpired() {
        purgeExpired(LocalDateTime.now());
    }

    @Transactional
    public Result purgeExpired(LocalDateTime now) {
        LocalDateTime cutoff = now.minus(RETENTION);
        List<Long> purgedIds = List.of();
        List<Long> candidates = commentRepository.findExpiredDeletedIds(cutoff);

        if (!candidates.isEmpty()) {
            commentRepository.purgeContent(candidates, cutoff, now);
            purgedIds = commentRepository.findPurgedIn(candidates);
        }

        int histories = purgedIds.isEmpty() ? 0 : historyRepository.deleteByTargets(Target.COMMENT, purgedIds);
        histories += historyRepository.deleteCreatedBefore(cutoff);

        if (!purgedIds.isEmpty() || histories > 0) {
            log.info("[보관 정리] 지운 댓글 원문 {}건, 변경 기록 {}건 파기", purgedIds.size(), histories);
        }

        return new Result(purgedIds.size(), histories);
    }

    public record Result(int purgedComments, int deletedHistories) {
    }
}
