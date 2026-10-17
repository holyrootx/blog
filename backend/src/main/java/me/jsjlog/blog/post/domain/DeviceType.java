package me.jsjlog.blog.post.domain;

import java.util.List;
import java.util.Locale;

/**
 * 글을 연 기기의 거친 분류. 검색 봇도 여기서 가른다.
 *
 * <p>브라우저가 보낸 원래 문자열(User-Agent)은 저장하지 않고, 여기서 몇 가지 중 하나로 접은 값만 남긴다.
 * 원래 문자열은 기기 모델과 버전까지 담고 있어 여러 기록을 이으면 사람을 알아보는 단서가 된다.</p>
 *
 * <p>정확하지 않다. 아이패드 사파리는 기본이 "데스크톱 사이트 요청" 이라 PC 로 잡히고,
 * 기기를 밝히지 않는 요청은 알 수 없음이 된다. 통계 화면에 그렇게 적는다.</p>
 */
public enum DeviceType {
    MOBILE,
    TABLET,
    DESKTOP,
    UNKNOWN,
    /**
     * 스스로 봇이라고 밝힌 요청. 사람의 조회가 아니라서 통계 숫자에서 빼고 따로 센다.
     * 이름을 숨긴 봇은 가려내지 못한다.
     */
    BOT;

    /**
     * 봇이 브라우저 정보에 남기는 표시. 이름 끝의 "bot" 하나로 가르지 않는다 —
     * 휴대폰 상표 "Cubot" 처럼 사람의 기기에도 들어 있다. 봇 이름 뒤에 버전이 붙는 꼴("Googlebot/2.1")과
     * 봇이 남기는 안내 주소("+http://...")를 본다.
     */
    private static final List<String> BOT_MARKERS = List.of(
            "bot/", "bot;", "bot-", "bot)", "+http", "crawler", "spider", "slurp",
            "yeti/", "daumoa", "facebookexternalhit", "kakaotalk-scrap"
    );

    /**
     * @param userAgent 요청의 User-Agent. 판정에만 쓰고 버린다
     * @param mobileHint 크롬 계열이 보내는 Sec-CH-UA-Mobile. "?1" 이면 휴대폰이다
     */
    public static DeviceType from(String userAgent, String mobileHint) {
        String agent = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);

        // 구글 봇의 휴대폰판은 안드로이드 휴대폰처럼 자신을 소개한다. 기기보다 먼저 본다
        if (BOT_MARKERS.stream().anyMatch(agent::contains)) {
            return BOT;
        }
        if ("?1".equals(mobileHint)) {
            return MOBILE;
        }
        if (agent.isBlank()) {
            return UNKNOWN;
        }

        // 안드로이드 태블릿은 "mobile" 을 빼고 보낸다. 휴대폰 판정보다 먼저 본다
        if (agent.contains("ipad") || agent.contains("tablet") || agent.contains("kindle") || agent.contains("silk")
                || (agent.contains("android") && !agent.contains("mobile"))) {
            return TABLET;
        }
        if (agent.contains("mobi") || agent.contains("iphone") || agent.contains("ipod")
                || agent.contains("windows phone")) {
            return MOBILE;
        }
        if (agent.contains("windows nt") || agent.contains("macintosh") || agent.contains("x11")
                || agent.contains("cros")) {
            return DESKTOP;
        }
        return UNKNOWN;
    }
}
