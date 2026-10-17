package me.jsjlog.blog.post.domain;

import java.util.Locale;

/**
 * 글을 연 기기의 거친 분류.
 *
 * <p>브라우저가 보낸 원래 문자열(User-Agent)은 저장하지 않고, 여기서 넷 중 하나로 접은 값만 남긴다.
 * 원래 문자열은 기기 모델과 버전까지 담고 있어 여러 기록을 이으면 사람을 알아보는 단서가 된다.</p>
 *
 * <p>정확하지 않다. 아이패드 사파리는 기본이 "데스크톱 사이트 요청" 이라 PC 로 잡히고,
 * 봇처럼 기기를 밝히지 않는 요청은 알 수 없음이 된다. 통계 화면에 그렇게 적는다.</p>
 */
public enum DeviceType {
    MOBILE,
    TABLET,
    DESKTOP,
    UNKNOWN;

    /**
     * @param userAgent 요청의 User-Agent. 판정에만 쓰고 버린다
     * @param mobileHint 크롬 계열이 보내는 Sec-CH-UA-Mobile. "?1" 이면 휴대폰이다
     */
    public static DeviceType from(String userAgent, String mobileHint) {
        if ("?1".equals(mobileHint)) {
            return MOBILE;
        }
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }

        String agent = userAgent.toLowerCase(Locale.ROOT);

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
