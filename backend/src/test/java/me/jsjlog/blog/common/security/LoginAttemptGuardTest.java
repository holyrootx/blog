package me.jsjlog.blog.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 몇 번 틀리면 막히고, 언제 풀리는가.
 *
 * <p>막는 대상이 <b>계정이 아니라 주소</b>라는 것이 이 장치의 핵심이라, 그 점을 특히 본다.
 * 계정을 막으면 관리자가 한 명뿐인 이 블로그에서는 차단 자체가 공격 수단이 된다.</p>
 */
class LoginAttemptGuardTest {

    private final LoginAttemptGuard guard = new LoginAttemptGuard();

    @Test
    @DisplayName("다섯 번 틀리면 막힌다")
    void blocksAfterFiveFailures() {
        for (int i = 0; i < 4; i++) {
            guard.recordFailure("1.1.1.1");
            assertThat(guard.isBlocked("1.1.1.1")).as("%d번째까지는 통과", i + 1).isFalse();
        }

        guard.recordFailure("1.1.1.1");

        assertThat(guard.isBlocked("1.1.1.1")).isTrue();
        assertThat(guard.remainingBlock("1.1.1.1")).isPositive();
    }

    @Test
    @DisplayName("한 주소가 막혀도 다른 주소는 멀쩡하다")
    void blocksOnlyTheOffendingAddress() {
        for (int i = 0; i < 5; i++) {
            guard.recordFailure("1.1.1.1");
        }

        assertThat(guard.isBlocked("1.1.1.1")).isTrue();
        // 여기가 무너지면 공격자가 주인을 쫓아낼 수 있다
        assertThat(guard.isBlocked("2.2.2.2")).isFalse();
    }

    @Test
    @DisplayName("들어오면 세던 것이 지워진다")
    void clearsOnSuccess() {
        for (int i = 0; i < 4; i++) {
            guard.recordFailure("1.1.1.1");
        }

        guard.clear("1.1.1.1");

        // 지워졌으니 다시 다섯 번을 채워야 막힌다
        for (int i = 0; i < 4; i++) {
            guard.recordFailure("1.1.1.1");
        }

        assertThat(guard.isBlocked("1.1.1.1")).isFalse();
    }

    @Test
    @DisplayName("한 번도 틀린 적 없는 주소는 막혀 있지 않다")
    void unknownAddressIsFree() {
        assertThat(guard.isBlocked("9.9.9.9")).isFalse();
        assertThat(guard.remainingBlock("9.9.9.9")).isZero();
    }

    @Test
    @DisplayName("막힌 뒤에 더 두드려도 차단이 길어지지 않는다")
    void keepsBlockLengthStable() {
        for (int i = 0; i < 5; i++) {
            guard.recordFailure("1.1.1.1");
        }

        var 처음 = guard.remainingBlock("1.1.1.1");

        // 실패 핸들러가 막힌 요청은 세지 않지만, 세더라도 늘어나면 안 된다
        for (int i = 0; i < 4; i++) {
            guard.recordFailure("1.1.1.1");
        }

        assertThat(guard.remainingBlock("1.1.1.1")).isLessThanOrEqualTo(처음);
    }
}
