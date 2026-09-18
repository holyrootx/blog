package me.jsjlog.blog.member.domain;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

/**
 * 제공자가 닉네임을 주지 않았을 때 쓸 기본 이름을 만든다.
 *
 * <p>카카오·네이버는 닉네임이 선택 동의 항목이라 동의를 안 받으면 이름 없이 로그인이
 * 들어온다. 그런데 회원의 닉네임은 필수 값이다. 그 자리를 메우는 것이 이 클래스다.</p>
 *
 * <p>닉네임에 unique 를 걸지 않기로 했으므로 <b>중복 확인 조회를 하지 않는다.</b>
 * 걸었다면 "이미 있나" 를 묻고 충돌하면 다시 뽑는 순환이 필요했고, 그 조회는 인덱스가
 * 없는 컬럼을 훑는 일이 된다.</p>
 *
 * <p>대신 뒤에 두 자리 숫자를 붙여 조합을 넉넉히 만든다. 이름이 겹쳐도 DB 는 받아 주지만
 * 한 댓글창에 같은 이름이 둘 있으면 누가 누구인지 읽을 수 없다. 닉네임을 남겨 두기로 한
 * 이유 자체가 대화를 읽을 수 있게 하려는 것이라, 겹침은 제약이 아니라 품질 문제로 다룬다.</p>
 */
@Component
public class NicknameGenerator {

    /**
     * 권위를 뜻하는 말(관리자·운영자·주인)은 넣지 않는다. 작성자 구분은 뱃지가 하는데
     * 이름부터 헷갈리면 뱃지를 보기 전에 오해한다.
     */
    private static final List<String> ADJECTIVES = List.of(
            "졸린", "조용한", "다정한", "성실한", "씩씩한", "따뜻한", "깔끔한", "부지런한",
            "상냥한", "활발한", "차분한", "든든한", "포근한", "유쾌한", "정직한", "소박한",
            "진지한", "엉뚱한", "느긋한", "잔잔한", "가벼운", "용감한", "슬기로운", "재빠른",
            "참한", "멋진", "신난", "너그러운",
            "멍청한", "못생긴", "게으른", "심술난", "어설픈", "수상한", "시끄러운", "촌스러운"
    );

    private static final List<String> NOUNS = List.of(
            "고양이", "너구리", "다람쥐", "참새", "토끼", "여우", "수달", "판다",
            "펭귄", "오징어", "문어", "거북이", "하마", "사슴", "올빼미", "두더지",
            "개구리", "달팽이", "고래", "고등어", "감자", "도토리", "붕어빵", "구름",
            "바람", "우산", "연필", "노트", "별빛", "모래"
    );

    /** 두 자리로 고정한다. 앞에 0 이 붙은 이름은 어색하고, 세 자리는 이름이 길어진다 */
    private static final int NUMBER_MIN = 10;
    private static final int NUMBER_BOUND = 100;

    /**
     * 조합 36 × 30 × 90 = 97,200 가지.
     *
     * 난수를 필드로 들고 있지 않은 이유는 이 값이 요청 스레드마다 따로 쓰이기 때문이다.
     * {@link ThreadLocalRandom} 은 스레드별로 하나씩이라 경쟁이 없다.
     */
    public String generate() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String adjective = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
        String noun = NOUNS.get(random.nextInt(NOUNS.size()));
        int number = random.nextInt(NUMBER_MIN, NUMBER_BOUND);

        return adjective + noun + number;
    }
}
