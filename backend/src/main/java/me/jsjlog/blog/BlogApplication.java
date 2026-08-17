package me.jsjlog.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 블로그 백엔드 애플리케이션의 시작점입니다.
 *
 * <p>이 클래스가 있는 {@code me.jsjlog.blog} 패키지부터 하위 패키지를 스캔하므로,
 * 컨트롤러, 서비스, 리포지토리 같은 Spring Bean은 이 패키지 아래에 배치합니다.</p>
 */
@EnableJpaAuditing
@SpringBootApplication
public class BlogApplication {

	/**
	 * 로컬 실행, 운영 서버 실행, 테스트 실행에서 모두 사용하는 애플리케이션 진입점입니다.
	 */
	public static void main(String[] args) {
		SpringApplication.run(BlogApplication.class, args);
	}
}
