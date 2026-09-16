package me.jsjlog.blog.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
public class AuditingConfig {

    /**
     * created_by · updated_by 에 들어갈 값.
     *
     * 로그인한 관리자가 있으면 그 아이디를, 없으면 "system" 을 넣는다.
     * 로그인 없이 일어나는 변경(서버 기동 시 초기화, 배치, 나중에 붙을 익명 댓글)도
     * 기록은 남아야 하므로 비워 두지 않는다.
     *
     * 인증 정보는 요청을 처리하는 스레드에 붙어 있고, 저장 시점에 읽힌다.
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of(currentAdmin());
    }

    private String currentAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 익명 요청에도 인증 객체가 하나 들어 있다. 이름이 "anonymousUser" 라
        // 그냥 쓰면 사람이 한 일처럼 보인다
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "system";
        }

        return authentication.getName();
    }
}
