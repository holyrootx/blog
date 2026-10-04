package me.jsjlog.blog.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 요청을 보낸 사람의 주소를 고른다.
 *
 * <p>{@code getRemoteAddr()} 를 그냥 쓸 수 없다. 이 서버는 Cloudflare 와 Nginx 뒤에 있어서
 * 그 값이 모든 요청에 대해 Nginx 주소(127.0.0.1) 로 나온다. 주소별로 세려고 만든 장치가
 * 전부 한 칸에 쌓이면, 한 사람이 틀린 탓에 모두가 막힌다.</p>
 *
 * <p><b>{@code CF-Connecting-IP} 를 직접 읽지 않는다.</b> 그 헤더는 Cloudflare 가 붙여 주는
 * 것인데, Cloudflare 를 거치지 않고 서버 주소를 직접 때리면 아무 값이나 적어 보낼 수 있다.
 * 시도할 때마다 다른 주소인 척하면 횟수를 세는 장치가 통째로 무력해진다.</p>
 *
 * <p>그래서 <b>같은 호스트의 Nginx 가 걸러 준 {@code X-Real-IP} 만 읽는다.</b>
 * 직접 접속한 클라이언트의 헤더는 무시한다. Nginx 쪽에 이렇게 둔다.</p>
 *
 * <pre>
 * set_real_ip_from  &lt;Cloudflare 대역&gt;;
 * real_ip_header    CF-Connecting-IP;
 * </pre>
 *
 * <p>이러면 Nginx 가 <b>접속해 온 곳이 Cloudflare 대역일 때만</b> 그 헤더를 믿고
 * {@code $remote_addr} 을 방문자 주소로 바꾼다. 직접 들어온 요청은 바꾸지 않으므로
 * 공격자가 무엇을 적어 보내든 제 주소가 그대로 남는다 — 위조할 수가 없다.</p>
 *
 * <p>이 설정이 없으면 {@code X-Real-IP} 에 Cloudflare 서버 주소가 담긴다. 그래도 위험하지는
 * 않고 세는 단위가 거칠어질 뿐이다. 다만 설정을 먼저 넣고 배포하는 편이 낫다.</p>
 */
@Component
public class ClientIpResolver {

    /** Nginx 가 {@code $remote_addr} 을 담아 주는 헤더. 이 서버가 믿는 유일한 출처다 */
    private static final String NGINX_HEADER = "X-Real-IP";

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        String realIp = request.getHeader(NGINX_HEADER);

        if (isLocalProxy(remoteAddress) && StringUtils.hasText(realIp)) {
            return realIp.trim();
        }

        // 운영 방화벽과 별개로, 직접 요청의 헤더로 로그인 제한 기준을 바꿀 수 없게 한다.
        return remoteAddress;
    }

    private boolean isLocalProxy(String address) {
        return "127.0.0.1".equals(address)
                || "::1".equals(address)
                || "0:0:0:0:0:0:0:1".equals(address);
    }
}
