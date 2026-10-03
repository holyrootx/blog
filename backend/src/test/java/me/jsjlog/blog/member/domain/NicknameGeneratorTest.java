package me.jsjlog.blog.member.domain;

import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NicknameGeneratorTest {

    private final NicknameGenerator generator = new NicknameGenerator();

    @Test
    @DisplayName("눈으로 확인할 표본")
    void printSamples() {
        IntStream.range(0, 20).forEach(index -> System.out.println("  " + generator.generate()));
    }

    @Test
    @DisplayName("한글 + 두 자리 숫자 형태로 나온다")
    void generatesKoreanWithTwoDigits() {
        IntStream.range(0, 500).forEach(index ->
                assertThat(generator.generate()).matches("^[가-힣]+[1-9][0-9]$"));
    }

    @Test
    @DisplayName("nickname 컬럼 길이(50)를 넘지 않는다")
    void fitsInColumn() {
        IntStream.range(0, 500).forEach(index ->
                assertThat(generator.generate()).hasSizeLessThanOrEqualTo(50));
    }

    @Test
    @DisplayName("같은 이름만 계속 나오지는 않는다")
    void producesVariety() {
        long distinct = IntStream.range(0, 200)
                .mapToObj(index -> generator.generate())
                .distinct()
                .count();

        // 조합이 97,200 가지라 200번 뽑으면 거의 전부 달라야 한다.
        // 난수를 안 쓰고 상수를 돌려주는 실수를 잡는 것이 목적이다
        assertThat(distinct).isGreaterThan(190);
    }
}
