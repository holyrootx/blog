package me.jsjlog.blog.support;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import me.jsjlog.blog.BlogApplication;
import me.jsjlog.blog.home.domain.BlogProfile;
import me.jsjlog.blog.home.repository.BlogProfileRepository;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.PostStatus;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 Spring 서버와 MySQL의 수명을 브라우저 검사와 함께 관리한다. 운영 설정 파일은 읽지 않는다. */
class AdminPostBrowserIntegrationTest {
	private static final String TITLE = "실제 API 통합 검사 글";
	private static final String CONTENT = "한글 본문과 이모지 🌱, 저장 후 다시 읽습니다.";
	private static final String EXCERPT = "로그인부터 발행까지 실제 HTTP로 확인합니다.";

	@Test
	void browserLoginSaveReopenAndPublishPersistInMySql() throws Exception {
		String schema = MySqlTestDatabase.prepare("browser_" + UUID.randomUUID().toString().replace("-", ""));
		Path frontend = Path.of("../frontend").toAbsolutePath().normalize();
		Process browser = null;
		try (var context = new SpringApplicationBuilder(BlogApplication.class).web(WebApplicationType.SERVLET).run(
				"--spring.config.location=classpath:/application.yaml",
				"--spring.profiles.active=browser-integration",
				"--server.address=127.0.0.1", "--server.port=0",
				"--server.servlet.session.cookie.secure=false",
				"--spring.datasource.url=" + MySqlTestDatabase.url(schema),
				"--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
				"--spring.datasource.username=" + MySqlTestDatabase.username(),
				"--spring.datasource.password=" + MySqlTestDatabase.password(),
				"--spring.jpa.hibernate.ddl-auto=update",
				"--spring.jpa.defer-datasource-initialization=true",
				"--spring.sql.init.mode=always",
				"--spring.sql.init.data-locations=classpath:db/common-code-seed.sql",
				"--blog.frontend.index-path=" + frontend.resolve("index.html"),
				"--blog.scheduled-publish.interval-ms=3600000")) {
			String password = UUID.randomUUID().toString();
			long adminId = context.getBean(MemberRepository.class).saveAndFlush(Member.ofLocalAdmin(
					"browser-test-admin", context.getBean(PasswordEncoder.class).encode(password), "통합 검사 관리자")).getId();
			long categoryId = context.getBean(CategoryRepository.class).saveAndFlush(new Category("통합 검사", 1L)).getId();
			context.getBean(BlogProfileRepository.class).saveAndFlush(new BlogProfile(
					"통합 검사 작성자", "테스트용 프로필", "개발자", null, null, null, null));
			int port = ((WebServerApplicationContext) context).getWebServer().getPort();
			ProcessBuilder command = new ProcessBuilder("node", "--test", "tests/admin-post.integration.test.mjs")
					.directory(frontend.toFile()).inheritIO();
			command.environment().put("BLOG_INTEGRATION_BACKEND_URL", "http://127.0.0.1:" + port);
			command.environment().put("BLOG_INTEGRATION_ADMIN_PASSWORD", password);
			command.environment().put("BLOG_INTEGRATION_TITLE", TITLE);
			command.environment().put("BLOG_INTEGRATION_CONTENT", CONTENT);
			command.environment().put("BLOG_INTEGRATION_EXCERPT", EXCERPT);
			browser = command.start();
			assertThat(browser.waitFor(120, TimeUnit.SECONDS)).as("Browser test must finish within 120 seconds").isTrue();
			assertThat(browser.exitValue()).as("Real backend browser checks").isZero();
			new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
				var posts = context.getBean(PostRepository.class).findAll();
				assertThat(posts).hasSize(1);
				var post = posts.getFirst();
				assertThat(post.getTitle()).isEqualTo(TITLE);
				assertThat(post.getContent()).isEqualTo(CONTENT + " 수정한 내용도 유지됩니다.");
				assertThat(post.getExcerpt()).isEqualTo(EXCERPT);
				assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
				assertThat(post.getAuthor().getId()).isEqualTo(adminId);
				assertThat(post.getCategory().getId()).isEqualTo(categoryId);
			});
		} finally {
			if (browser != null && browser.isAlive()) {
				var children = browser.descendants().toList();
				browser.destroy();
				if (!browser.waitFor(5, TimeUnit.SECONDS)) {
					browser.destroyForcibly().waitFor();
				}
				children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
			}
			try (var connection = MySqlTestDatabase.connect(""); var statement = connection.createStatement()) {
				statement.execute("DROP DATABASE `" + schema + "`");
			}
		}
	}
}
