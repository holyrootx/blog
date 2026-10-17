package me.jsjlog.blog.post.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceTypeTest {

    @Test
    @DisplayName("휴대폰·태블릿·PC 를 브라우저 정보로 거칠게 나눈다")
    void classifiesCommonBrowsers() {
        assertThat(DeviceType.from("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15"
                + " (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", null)).isEqualTo(DeviceType.MOBILE);
        assertThat(DeviceType.from("Mozilla/5.0 (Linux; Android 15; SM-S928N) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36", null)).isEqualTo(DeviceType.MOBILE);
        assertThat(DeviceType.from("Mozilla/5.0 (Linux; Android 14; SM-X710) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/130.0 Safari/537.36", null)).isEqualTo(DeviceType.TABLET);
        assertThat(DeviceType.from("Mozilla/5.0 (iPad; CPU OS 16_0 like Mac OS X) AppleWebKit/605.1.15", null))
                .isEqualTo(DeviceType.TABLET);
        assertThat(DeviceType.from("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/130.0 Safari/537.36", null)).isEqualTo(DeviceType.DESKTOP);
        assertThat(DeviceType.from("Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15", null))
                .isEqualTo(DeviceType.DESKTOP);
        assertThat(DeviceType.from("Mozilla/5.0 (X11; Linux x86_64) Gecko/20100101 Firefox/131.0", null))
                .isEqualTo(DeviceType.DESKTOP);
    }

    @Test
    @DisplayName("스스로 봇이라고 밝힌 요청은 휴대폰처럼 소개해도 검색 봇이다. 상표 이름에 bot 이 든 휴대폰은 휴대폰이다")
    void classifiesBots() {
        assertThat(DeviceType.from("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", null))
                .isEqualTo(DeviceType.BOT);
        assertThat(DeviceType.from("Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5X Build/MMB29P) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
                + " (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", "?1")).isEqualTo(DeviceType.BOT);
        assertThat(DeviceType.from("Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)", null))
                .isEqualTo(DeviceType.BOT);
        assertThat(DeviceType.from("Mozilla/5.0 (compatible; Yeti/1.1; +https://naver.me/spd)", null))
                .isEqualTo(DeviceType.BOT);
        assertThat(DeviceType.from("Mozilla/5.0 (compatible; Daum/4.1; +https://cs.daum.net/faq/15/4118.html)", null))
                .isEqualTo(DeviceType.BOT);
        assertThat(DeviceType.from("facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)", null))
                .isEqualTo(DeviceType.BOT);
        assertThat(DeviceType.from("Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)", null))
                .isEqualTo(DeviceType.BOT);
        assertThat(DeviceType.from("Mozilla/5.0 (Linux; Android 10; CUBOT X30) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36", null)).isEqualTo(DeviceType.MOBILE);
    }

    @Test
    @DisplayName("기기를 밝히지 않으면 알 수 없음이고, 크롬의 휴대폰 신호가 있으면 휴대폰이다")
    void unknownAndClientHint() {
        assertThat(DeviceType.from(null, null)).isEqualTo(DeviceType.UNKNOWN);
        assertThat(DeviceType.from("  ", null)).isEqualTo(DeviceType.UNKNOWN);
        assertThat(DeviceType.from("curl/8.7.1", null)).isEqualTo(DeviceType.UNKNOWN);
        assertThat(DeviceType.from(null, "?1")).isEqualTo(DeviceType.MOBILE);
        assertThat(DeviceType.from("Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "?0")).isEqualTo(DeviceType.DESKTOP);
    }
}
