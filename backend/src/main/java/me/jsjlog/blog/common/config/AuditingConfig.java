package me.jsjlog.blog.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

@Configuration
public class AuditingConfig {

    /**
     * 인증 기능 없어서 기본값으로 system 들어가도록 설정
     * @return
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of("system");
    }
}
