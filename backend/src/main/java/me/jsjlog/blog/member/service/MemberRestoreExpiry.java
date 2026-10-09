package me.jsjlog.blog.member.service;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 후 복원 기간이 지난 정보를 지운다. 하루 한 번 돈다.
 *
 * <p>로그인 판단은 기간을 직접 보므로, 이 작업이 늦게 돌아도 기간 지난 계정은 복원되지 않는다.
 * 이 작업은 남아 있는 제공자 ID 를 실제로 파기하는 일을 맡는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberRestoreExpiry {

    private final MemberStatusService memberStatusService;

    // 댓글 보관 정리(04:30)와 겹치지 않게 조금 뒤에 돈다
    @Scheduled(cron = "${blog.retention.member-restore-cron:0 40 4 * * *}", zone = "Asia/Seoul")
    public void expire() {
        int expired = memberStatusService.expireRestores(LocalDateTime.now());
        if (expired > 0) {
            log.info("[보관 정리] 복원 기간이 지난 탈퇴 회원 {}명의 복원 정보 파기", expired);
        }
    }
}
