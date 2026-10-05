package me.jsjlog.blog.history.service;

import java.time.LocalDateTime;
import java.time.Period;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.history.repository.ContentHistoryRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관 기간이 지난 변경 기록을 지운다.
 *
 * 기록에는 회원이 고치거나 지우기 전 댓글 원문이 들어 있다. 개인정보 처리방침에
 * 6개월 보관 뒤 파기한다고 적었으므로 그보다 오래 두지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentHistoryRetention {

    public static final Period RETENTION = Period.ofMonths(6);

    private final ContentHistoryRepository historyRepository;

    // 하루 한 번이면 충분하다. 손님이 적은 새벽에 돈다
    @Transactional
    @Scheduled(cron = "${blog.history.purge-cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void purgeExpired() {
        purgeExpired(LocalDateTime.now());
    }

    @Transactional
    public int purgeExpired(LocalDateTime now) {
        int deleted = historyRepository.deleteCreatedBefore(now.minus(RETENTION));

        if (deleted > 0) {
            log.info("[기록 파기] 보관 기간이 지난 변경 기록 {}건 삭제", deleted);
        }

        return deleted;
    }
}
