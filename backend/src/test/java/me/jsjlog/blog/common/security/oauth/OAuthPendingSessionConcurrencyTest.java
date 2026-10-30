package me.jsjlog.blog.common.security.oauth;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.repository.MemberRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** DB 조회 직후 요청을 멈춰, 실제 세션 전환과 늦은 조회가 겹치는 순서를 검증한다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:oauth_pending_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(OAuthPendingSessionConcurrencyTest.ProbeConfiguration.class)
class OAuthPendingSessionConcurrencyTest {
	@Autowired MockMvc mvc;
	@Autowired MemberRepository members;
	@Autowired OAuth2LoginSuccessHandler oauthSuccess;
	@Autowired RefreshProbe probe;

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void aLateReadMustNotOverwriteANewerOAuthLogin(boolean sameIdentity) throws Exception {
		MockHttpSession session = pending();
		var original = (PendingOAuthSession) session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME);
		String nextId = sameIdentity ? original.providerUserId() : "new-" + UUID.randomUUID();
		MvcResult result = delayedRead(session, () -> {
			var info = new OAuthUserInfo(AuthProvider.GOOGLE, nextId, "새 계정", null, null);
			completeOAuth(session, PendingOAuthPrincipal.signup(info, "새 계정"));
			assertThat(((PendingOAuthSession) session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME))
					.providerUserId()).isEqualTo(nextId);
		});
		assertExpired(result);
		assertThat(session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME)).isNotSameAs(original);
		assertThat(((PendingOAuthSession) session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME))
				.providerUserId()).isEqualTo(nextId);
		mvc.perform(post("/api/v1/auth/oauth/signup").session(session).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"새 계정으로 가입\"}"))
				.andExpect(status().isOk());
		assertThat(members.findByProviderAndProviderUserId(AuthProvider.GOOGLE, nextId))
				.as("가장 최근에 로그인한 소셜 계정으로 가입").isPresent();
	}

	@Test
	void aLateReadMustNotRecreatePendingStateAfterSignupHasCompleted() throws Exception {
		MockHttpSession session = pending();
		MvcResult result = delayedRead(session, () -> {
			mvc.perform(post("/api/v1/auth/oauth/signup").session(session).with(csrf())
							.contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"가입 완료\"}"))
					.andExpect(status().isOk());
			assertThat(session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME)).isNull();
		});
		assertExpired(result);
		assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
		assertThat(session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME))
				.as("가입 완료 후 대기 정보 제거 유지").isNull();
	}

	@Test
	void aLateReadMustNotReturnInternalErrorAfterLogout() throws Exception {
		MockHttpSession session = pending();
		MvcResult result = delayedRead(session, () -> {
			mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
					.andExpect(status().isOk());
			assertThat(session.isInvalid()).isTrue();
		});
		assertExpired(result);
	}

	private void assertExpired(MvcResult result) throws Exception {
		status().isUnauthorized().match(result);
		jsonPath("$.code").value("OAUTH_SESSION_EXPIRED").match(result);
	}

	private MockHttpSession pending() {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(PendingOAuthSession.ATTRIBUTE_NAME,
				new PendingOAuthSession(AuthProvider.GOOGLE, "old-" + UUID.randomUUID(), "이전 계정",
						null, null, OAuthLoginState.SIGNUP_REQUIRED));
		return session;
	}

	private MvcResult delayedRead(MockHttpSession session, Step meanwhile) throws Exception {
		Gate gate = new Gate();
		probe.gate = gate;
		try (var executor = Executors.newSingleThreadExecutor()) {
			var future = executor.submit(() -> mvc.perform(get("/api/v1/auth/oauth/pending").session(session)).andReturn());
			try {
				assertThat(gate.loaded.await(10, TimeUnit.SECONDS)).isTrue();
				meanwhile.run();
			} finally {
				gate.resume.countDown();
			}
			return future.get(10, TimeUnit.SECONDS);
		} finally {
			probe.gate = null;
		}
	}

	private void completeOAuth(MockHttpSession session, PendingOAuthPrincipal principal) throws Exception {
		var request = new MockHttpServletRequest();
		request.setSession(session);
		var response = new MockHttpServletResponse();
		var authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
		new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response);
		var context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		try {
			new HttpSessionSecurityContextRepository().saveContext(context, request, response);
			oauthSuccess.onAuthenticationSuccess(request, response, authentication);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	@FunctionalInterface interface Step { void run() throws Exception; }
	@TestConfiguration static class ProbeConfiguration {
		@Bean RefreshProbe refreshProbe() { return new RefreshProbe(); }
	}
	@Aspect static class RefreshProbe {
		volatile Gate gate;
		@Around("execution(* me.jsjlog.blog.member.service.OAuthSignupService.refreshPending(..))")
		Object afterRefresh(ProceedingJoinPoint call) throws Throwable {
			Object result = call.proceed();
			Gate current = gate;
			if (current != null) {
				current.loaded.countDown();
				if (!current.resume.await(10, TimeUnit.SECONDS)) {
					throw new AssertionError("대기 요청 재개 시간 초과");
				}
			}
			return result;
		}
	}
	static class Gate {
		final CountDownLatch loaded = new CountDownLatch(1);
		final CountDownLatch resume = new CountDownLatch(1);
	}
}
