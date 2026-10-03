package me.jsjlog.blog.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 어느 주소를 세는가.
 *
 * <p>여기가 틀리면 차단 장치 전체가 무의미해진다 — 공격자가 매번 다른 사람인 척할 수
 * 있게 되기 때문이다.</p>
 */
class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    @DisplayName("Nginx 가 넣어 준 X-Real-IP 를 쓴다")
    void prefersNginxHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "1.2.3.4");
        request.setRemoteAddr("127.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("1.2.3.4");
    }

    @Test
    @DisplayName("CF-Connecting-IP 는 보지 않는다 — 직접 접속하면 지어낼 수 있는 값이다")
    void ignoresForgeableCloudflareHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", "9.9.9.9");
        request.setRemoteAddr("203.0.113.5");

        // 공격자가 적어 보낸 9.9.9.9 를 따라가면 시도마다 다른 주소가 되어 차단을 피한다
        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("X-Forwarded-For 도 보지 않는다 — 같은 이유로 지어낼 수 있다")
    void ignoresForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "9.9.9.9, 10.0.0.1");
        request.setRemoteAddr("203.0.113.5");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("프록시 없이 띄운 개발 환경에서는 접속 주소를 그대로 쓴다")
    void fallsBackToRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.0.11");

        assertThat(resolver.resolve(request)).isEqualTo("192.168.0.11");
    }
}
