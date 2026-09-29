package me.jsjlog.blog.common.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * 같은 주소에서 비밀번호를 계속 틀리면 잠시 막는다.
 *
 * <p>막기 전에는 아무 장치가 없었다. 초당 수십 번을 시도해도 서버가 그대로 받아 줬다.</p>
 *
 * <p><b>계정이 아니라 주소를 막는다.</b> 관리자가 한 명뿐이라 계정을 잠그면 그것이 곧
 * 공격 수단이 된다 — 아무나 일부러 다섯 번 틀려서 주인을 제 블로그에서 쫓아낼 수 있다.
 * 주소를 막으면 공격자만 막힌다.</p>
 *
 * <p><b>비밀번호를 검사하기 전에 막아야 한다.</b> BCrypt 검증 한 번이 42ms 다. 막지 않으면
 * 비밀번호를 못 맞히더라도 CPU 를 계속 태울 수 있고, 서버가 한 대라 그 자체로 블로그가
 * 느려진다. 그래서 {@link AdminLoginFilter} 가 인증을 시작하기 전에 여기를 먼저 본다.</p>
 *
 * <p>기억은 메모리에 둔다. 서버를 다시 띄우면 지워지는데, 다시 띄우는 일은 배포할 때뿐이고
 * 그 순간 공격자가 마침 세고 있던 중일 가능성은 낮다. DB 에 두면 로그인 실패마다 쓰기가
 * 생기는데, 그건 실패를 많이 만들수록 서버가 힘들어진다는 뜻이라 오히려 거꾸로다.</p>
 */
@Component
public class LoginAttemptGuard {

    /** 이만큼 틀리면 막는다 */
    private static final int MAX_FAILURES = 5;

    /** 막아 두는 시간 */
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(10);

    /**
     * 마지막 실패 이후 이만큼 지나면 세던 것을 잊는다.
     *
     * <p>어제 한 번 잘못 친 것이 오늘 것과 합쳐지면 안 된다. 사람은 원래 가끔 틀린다.</p>
     */
    private static final Duration FORGET_AFTER = Duration.ofMinutes(30);

    /**
     * 이 수를 넘으면 오래된 것부터 걷어낸다.
     *
     * <p>주소마다 칸이 하나씩 생기므로, 주소를 바꿔 가며 때리면 이 표가 한없이 커진다.
     * 그것 자체가 메모리를 쓰는 공격이 된다.</p>
     */
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** @return 지금 막혀 있으면 true */
    public boolean isBlocked(String clientIp) {
        Attempt attempt = attempts.get(clientIp);

        if (attempt == null) {
            return false;
        }

        if (attempt.blockedUntil != null && Instant.now().isBefore(attempt.blockedUntil)) {
            return true;
        }

        // 막힌 시간이 지났거나 오래 조용했으면 없던 일로 한다
        if (attempt.isStale()) {
            attempts.remove(clientIp);
        }

        return false;
    }

    /** 남은 시간. 막혀 있지 않으면 0 */
    public Duration remainingBlock(String clientIp) {
        Attempt attempt = attempts.get(clientIp);

        if (attempt == null || attempt.blockedUntil == null) {
            return Duration.ZERO;
        }

        Duration left = Duration.between(Instant.now(), attempt.blockedUntil);

        return left.isNegative() ? Duration.ZERO : left;
    }

    public void recordFailure(String clientIp) {
        evictIfCrowded();

        attempts.compute(clientIp, (ignored, existing) -> {
            Attempt attempt = (existing == null || existing.isStale()) ? new Attempt() : existing;

            attempt.lastFailedAt = Instant.now();

            if (attempt.count.incrementAndGet() >= MAX_FAILURES) {
                attempt.blockedUntil = Instant.now().plus(BLOCK_DURATION);
                attempt.count.set(0);
            }

            return attempt;
        });
    }

    /** 들어왔으면 세던 것을 지운다. 제 비밀번호를 아는 사람이 몇 번 틀린 것은 공격이 아니다 */
    public void clear(String clientIp) {
        attempts.remove(clientIp);
    }

    /**
     * 표가 너무 커지면 지난 것들을 걷어낸다.
     *
     * <p>지울 것이 없을 만큼 전부 살아 있으면 통째로 비운다. 기억을 잃는 편이 메모리를
     * 잃는 편보다 낫다 — 기억은 다시 쌓이지만 서버가 죽으면 블로그가 멈춘다.</p>
     */
    private void evictIfCrowded() {
        if (attempts.size() < MAX_TRACKED) {
            return;
        }

        attempts.values().removeIf(Attempt::isStale);

        if (attempts.size() >= MAX_TRACKED) {
            attempts.clear();
        }
    }

    private static final class Attempt {

        private final AtomicInteger count = new AtomicInteger();
        private Instant lastFailedAt = Instant.now();
        private Instant blockedUntil;

        private boolean isStale() {
            Instant now = Instant.now();

            if (blockedUntil != null && now.isBefore(blockedUntil)) {
                return false;
            }

            return lastFailedAt.plus(FORGET_AFTER).isBefore(now);
        }
    }
}
