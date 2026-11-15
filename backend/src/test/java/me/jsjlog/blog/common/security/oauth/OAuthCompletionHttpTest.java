package me.jsjlog.blog.common.security.oauth;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 Tomcat 세션·쿠키·CSRF 경계를 확인한다. 외부 제공자 인증만 로컬 콜백 fixture로 대신한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.url=jdbc:h2:mem:oauth_completion_http;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa", "spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({OAuthCompletionHttpTest.FakeOAuthCallback.class,
		OAuthCompletionSessionConcurrencyTest.ProbeConfiguration.class})
class OAuthCompletionHttpTest {
	@LocalServerPort int port;
	@Autowired ObjectMapper json;
	@Autowired MemberRepository members;
	@Autowired FakeOAuthCallback callback;
	@Autowired OAuthCompletionSessionConcurrencyTest.CompletionProbe probe;
	HttpClient client;

	@BeforeEach
	void client() {
		client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
				.connectTimeout(Duration.ofSeconds(5)).build();
	}

	@AfterEach
	void close() {
		probe.gate = null;
		client.close();
	}

	@Test
	void normalCompletionRotatesTheRealSessionAndCsrfToken() throws Exception {
		String subject = "http-" + UUID.randomUUID();
		startPending(subject);
		HttpSession session = callback.sessions.get(subject);
		String oldId = session.getId();
		assertThat(session.getAttribute(WebUtils.SESSION_MUTEX_ATTRIBUTE)).isNotNull();
		Csrf csrf = csrf();
		assertThat(send(post("/api/v1/auth/oauth/signup", csrf)).statusCode()).isEqualTo(200);
		assertThat(session.getId()).isNotEqualTo(oldId);
		assertThat(json.readTree(get("/api/v1/auth/me").body()).path("data").path("nickname").asText())
				.isEqualTo("HTTP 가입 검사");
		assertThat(send(post("/api/v1/auth/logout", csrf)).statusCode()).isEqualTo(403);
		assertThat(send(post("/api/v1/auth/logout", csrf())).statusCode()).isEqualTo(200);
		assertThat(json.readTree(get("/api/v1/auth/me").body()).path("data").isNull()).isTrue();
	}

	@Test
	void logoutBeforeLateCompletionCannotIssueANewAuthenticatedCookie() throws Exception {
		String subject = "http-" + UUID.randomUUID();
		startPending(subject);
		Csrf csrf = csrf();
		var gate = new OAuthCompletionSessionConcurrencyTest.Gate();
		probe.gate = gate;
		var completion = client.sendAsync(post("/api/v1/auth/oauth/signup", csrf), HttpResponse.BodyHandlers.ofString());
		try {
			assertThat(gate.loaded.await(10, TimeUnit.SECONDS)).isTrue();
			assertThat(send(post("/api/v1/auth/logout", csrf)).statusCode()).isEqualTo(200);
		} finally {
			gate.resume.countDown();
		}
		var result = completion.get(10, TimeUnit.SECONDS);
		assertThat(result.statusCode()).isEqualTo(401);
		assertThat(result.headers().allValues("set-cookie")).isEmpty();
		assertThat(json.readTree(result.body()).path("code").asText()).isEqualTo("OAUTH_SESSION_EXPIRED");
		assertThat(json.readTree(get("/api/v1/auth/me").body()).path("data").isNull()).isTrue();
		assertThat(members.findByProviderAndProviderUserId(AuthProvider.GOOGLE, subject)).isPresent();
	}

	@Test
	void newerOAuthPendingIdentitySurvivesTheLateHttpResponse() throws Exception {
		String subject = "http-" + UUID.randomUUID();
		String next = "next-" + UUID.randomUUID();
		startPending(subject);
		Csrf csrf = csrf();
		var gate = new OAuthCompletionSessionConcurrencyTest.Gate();
		probe.gate = gate;
		var completion = client.sendAsync(post("/api/v1/auth/oauth/signup", csrf), HttpResponse.BodyHandlers.ofString());
		try {
			assertThat(gate.loaded.await(10, TimeUnit.SECONDS)).isTrue();
			startPending(next);
		} finally {
			gate.resume.countDown();
		}
		var result = completion.get(10, TimeUnit.SECONDS);
		assertThat(result.statusCode()).isEqualTo(401);
		assertThat(result.headers().allValues("set-cookie")).isEmpty();
		HttpSession session = callback.sessions.get(next);
		assertThat(((PendingOAuthSession) session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME)).providerUserId())
				.isEqualTo(next);
		assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
		assertThat(get("/api/v1/auth/oauth/pending").statusCode()).isEqualTo(200);
		assertThat(json.readTree(get("/api/v1/auth/me").body()).path("data").isNull()).isTrue();
	}

	private void startPending(String subject) throws Exception {
		assertThat(get("/login/oauth2/code/completion-test?subject=" + subject).statusCode()).isEqualTo(302);
	}

	private Csrf csrf() throws Exception {
		var data = json.readTree(get("/api/v1/auth/csrf").body()).path("data");
		return new Csrf(data.path("headerName").asText(), data.path("token").asText());
	}

	private HttpRequest post(String path, Csrf csrf) {
		return HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20))
				.header("Content-Type", "application/json").header(csrf.header(), csrf.token())
				.POST(HttpRequest.BodyPublishers.ofString("{\"nickname\":\"HTTP 가입 검사\"}")).build();
	}

	private HttpResponse<String> get(String path) throws Exception {
		return send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20)).GET().build());
	}

	private HttpResponse<String> send(HttpRequest request) throws Exception {
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
	private record Csrf(String header, String token) { }

	@TestConfiguration(proxyBeanMethods = false)
	@RestController
	static class FakeOAuthCallback {
		@Autowired OAuth2LoginSuccessHandler success;
		final Map<String, HttpSession> sessions = new ConcurrentHashMap<>();

		@GetMapping("/login/oauth2/code/completion-test")
		void callback(@RequestParam String subject, HttpServletRequest request, HttpServletResponse response) throws Exception {
			request.getSession();
			var info = new OAuthUserInfo(AuthProvider.GOOGLE, subject, "HTTP 가입 검사", null, null);
			var principal = PendingOAuthPrincipal.signup(info, "HTTP 가입 검사");
			var authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
			new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response);
			var context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
			new HttpSessionSecurityContextRepository().saveContext(context, request, response);
			sessions.put(subject, request.getSession(false));
			success.onAuthenticationSuccess(request, response, authentication);
		}
	}
}
