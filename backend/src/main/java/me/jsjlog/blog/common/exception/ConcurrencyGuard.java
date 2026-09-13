package me.jsjlog.blog.common.exception;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 수정 충돌 감지.
 *
 * 화면이 불러왔던 updatedAt 을 저장 때 그대로 돌려보내고, 서버는 지금 값과 비교한다.
 * 다르면 그 사이 다른 곳에서 고쳐진 것이므로 덮어쓰지 않고 거절한다.
 *
 * @Version 컬럼을 쓰지 않은 이유는 DB 스키마 변경이 필요하기 때문이다.
 * updatedAt 은 BaseEntity 에 이미 있어 추가 컬럼 없이 같은 목적을 달성한다.
 */
public final class ConcurrencyGuard {

    private ConcurrencyGuard() {
    }

    /**
     * @param expected 화면이 들고 있던 값. null 이면 검사하지 않는다
     *                 (이 값을 안 보내는 호출자까지 막지는 않는다)
     * @param actual   지금 서버 값
     */
    public static void check(LocalDateTime expected, LocalDateTime actual) {
        if (expected == null) {
            return;
        }

        if (!Objects.equals(expected, actual)) {
            throw new BlogException(ErrorCode.MODIFIED_BY_OTHERS);
        }
    }
}
