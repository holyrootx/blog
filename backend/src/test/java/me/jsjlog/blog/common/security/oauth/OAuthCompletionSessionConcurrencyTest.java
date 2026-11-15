package me.jsjlog.blog.common.security.oauth;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:oauth_completion_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Import(OAuthCompletionSessionConcurrencyTest.ProbeConfiguration.class)
class OAuthCompletionSessionConcurrencyTest {
	@Autowired MockMvc mvc;
	@Autowired MemberRepository members;
	@Autowired OAuth2LoginSuccessHandler oauthSuccess;
	@Autowired CompletionProbe probe;

	@ParameterizedTest
	@ValueSource(strings = {"signup", "reactivate", "rejoin"})
	void unchangedSessionCompletesNormally(String action) throws Exception {
		MockHttpSession session = pending(action);
		MvcResult result = complete(action, session);
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME)).isNull();
		assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
				.isInstanceOf(SecurityContext.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"signup", "reactivate", "rejoin"})
	void oldCompletionCannotReplaceNewerOAuthPendingIdentity(String action) throws Exception {
		MockHttpSession session = pending(action);
		Object[] newer = new Object[1];
		MvcResult result = delayedCompletion(action, session, () -> {
			var info = new OAuthUserInfo(AuthProvider.GOOGLE, "new-" + UUID.randomUUID(),
					"New pending account", null, null);
			completeOAuth(session, PendingOAuthPrincipal.signup(info, "New pending account"));
			newer[0] = session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME);
			assertThat(newer[0]).isNotNull();
			assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
		});
		Object pendingAfter = session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME);
		Object contextAfter = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		System.out.printf("REVIEW action=%s interleave=new-oauth response=%d newerPendingRetained=%s authenticated=%s%n",
				action, result.getResponse().getStatus(), pendingAfter == newer[0], contextAfter != null);
		assertSoftly(check -> {
			check.assertThat(result.getResponse().getStatus()).as("old request is rejected").isEqualTo(401);
			check.assertThat(pendingAfter).as("newer OAuth pending state survives").isSameAs(newer[0]);
			check.assertThat(contextAfter).as("old identity must not be authenticated").isNull();
		});
	}

	@ParameterizedTest
	@ValueSource(strings = {"signup", "reactivate", "rejoin"})
	void oldCompletionCannotReauthenticateAfterLogout(String action) throws Exception {
		MockHttpSession session = pending(action);
		var original = (PendingOAuthSession) session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME);
		MvcResult result = delayedCompletion(action, session, () -> {
			mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf())).andExpect(status().isOk());
			assertThat(session.isInvalid()).isTrue();
		});
		var returnedSession = result.getRequest().getSession(false);
		Object contextAfter = returnedSession == null ? null :
				returnedSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		System.out.printf("REVIEW action=%s interleave=logout response=%d newSession=%s authenticated=%s%n",
				action, result.getResponse().getStatus(), returnedSession != null, contextAfter != null);
		assertSoftly(check -> {
			check.assertThat(result.getResponse().getStatus()).as("old request is rejected").isEqualTo(401);
			check.assertThat(contextAfter).as("logout must not be undone").isNull();
			check.assertThat(returnedSession).as("expired session must not be recreated").isNull();
		});
		assertThat(members.findByProviderAndProviderUserId(original.provider(), original.providerUserId()))
				.as("the already committed member change is available to a fresh OAuth login").isPresent();
	}

	@ParameterizedTest
	@ValueSource(strings = {"signup", "reactivate", "rejoin"})
	void oldCompletionCannotReplaceANewerAuthenticatedAccount(String action) throws Exception {
		MockHttpSession session = pending(action);
		Member next = members.saveAndFlush(Member.ofSocial(AuthProvider.GOOGLE,
				"next-" + UUID.randomUUID(), "New member", null, null));
		MemberPrincipal principal = MemberPrincipal.ofSocial(next, System.nanoTime());
		MvcResult result = delayedCompletion(action, session, () -> completeOAuth(session, principal));
		assertThat(result.getResponse().getStatus()).isEqualTo(401);
		SecurityContext current = (SecurityContext) session.getAttribute(
				HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		assertThat(current.getAuthentication().getPrincipal()).isSameAs(principal);
		assertThat(session.getAttribute(PendingOAuthSession.ATTRIBUTE_NAME)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"signup", "reactivate", "rejoin"})
	void expiryDuringFinalAuthenticationMustNotCreateAnotherSession(String action) throws Exception {
		MockHttpSession session = pending(action);
		Gate gate = new Gate();
		probe.registrationGate = gate;
		try (var executor = Executors.newSingleThreadExecutor()) {
			var future = executor.submit(() -> complete(action, session));
			try {
				assertThat(gate.loaded.await(10, TimeUnit.SECONDS)).isTrue();
				session.invalidate();
			} finally {
				gate.resume.countDown();
			}
			MvcResult result = future.get(10, TimeUnit.SECONDS);
			assertThat(result.getResponse().getStatus()).isEqualTo(401);
			assertThat(result.getRequest().getSession(false)).isNull();
		} finally {
			probe.registrationGate = null;
		}
	}

	@Test
	void logoutDuringFinalAuthenticationRunsAfterTheAtomicCompletion() throws Exception {
		MockHttpSession session = pending("signup");
		Gate gate = new Gate();
		probe.registrationGate = gate;
		try (var executor = Executors.newFixedThreadPool(2)) {
			var completion = executor.submit(() -> complete("signup", session));
			assertThat(gate.loaded.await(10, TimeUnit.SECONDS)).isTrue();
			var logout = executor.submit(() -> mvc.perform(post("/api/v1/auth/logout")
					.session(session).with(csrf())).andReturn());
			try {
				assertThatThrownBy(() -> logout.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
			} finally {
				gate.resume.countDown();
			}
			assertThat(completion.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
			assertThat(logout.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
			assertThat(session.isInvalid()).isTrue();
		} finally {
			gate.resume.countDown();
			probe.registrationGate = null;
		}
	}

	@Test
	void invalidCsrfLogoutCannotCancelAnInFlightSignup() throws Exception {
		MockHttpSession session = pending("signup");
		MvcResult result = delayedCompletion("signup", session, () ->
				mvc.perform(post("/api/v1/auth/logout").session(session)).andExpect(status().isForbidden()));
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
	}

	private MockHttpSession pending(String action) {
		String subject = "review-" + UUID.randomUUID();
		boolean recovery = !action.equals("signup");
		if (recovery) {
			Member member = Member.ofSocial(AuthProvider.GOOGLE, subject, "Withdrawn review account", null, null);
			member.withdraw(LocalDateTime.now());
			members.saveAndFlush(member);
		}
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(PendingOAuthSession.ATTRIBUTE_NAME,
				new PendingOAuthSession(AuthProvider.GOOGLE, subject, "Review account", null, null,
						recovery ? OAuthLoginState.REACTIVATION_REQUIRED : OAuthLoginState.SIGNUP_REQUIRED));
		return session;
	}

	private MvcResult complete(String action, MockHttpSession session) throws Exception {
		return mvc.perform(post("/api/v1/auth/oauth/" + action).session(session).with(csrf())
				.with(request -> {
					request.setRequestedSessionId(session.getId());
					return request;
				})
				.contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"Review account\"}")).andReturn();
	}

	private MvcResult delayedCompletion(String action, MockHttpSession session, Step meanwhile) throws Exception {
		Gate gate = new Gate();
		probe.gate = gate;
		try (var executor = Executors.newSingleThreadExecutor()) {
			var future = executor.submit(() -> complete(action, session));
			try {
				assertThat(gate.loaded.await(10, TimeUnit.SECONDS)).as("service completed before interleave").isTrue();
				meanwhile.run();
			} finally {
				gate.resume.countDown();
			}
			return future.get(10, TimeUnit.SECONDS);
		} finally {
			probe.gate = null;
		}
	}

	private void completeOAuth(MockHttpSession session, OAuth2User principal) throws Exception {
		var request = new MockHttpServletRequest();
		request.setSession(session);
		request.setRequestedSessionId(session.getId());
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
		@Bean CompletionProbe completionProbe() { return new CompletionProbe(); }
	}
	@Aspect static class CompletionProbe {
		volatile Gate gate;
		volatile Gate registrationGate;
		@Around("execution(* me.jsjlog.blog.common.security.MemberSessionManager.onAuthentication(..))")
		Object afterRegistration(ProceedingJoinPoint call) throws Throwable {
			Object result = call.proceed();
			await(registrationGate);
			return result;
		}
		@Around("execution(* me.jsjlog.blog.member.service.OAuthSignupService.signup(..)) || " +
				"execution(* me.jsjlog.blog.member.service.OAuthSignupService.reactivate(..)) || " +
				"execution(* me.jsjlog.blog.member.service.OAuthSignupService.rejoin(..))")
		Object afterCompletion(ProceedingJoinPoint call) throws Throwable {
			Object result = call.proceed();
			await(gate);
			return result;
		}
		private void await(Gate current) throws InterruptedException {
			if (current != null) {
				current.loaded.countDown();
				if (!current.resume.await(10, TimeUnit.SECONDS)) {
					throw new AssertionError("Completion gate timed out");
				}
			}
		}
	}
	static class Gate {
		final CountDownLatch loaded = new CountDownLatch(1);
		final CountDownLatch resume = new CountDownLatch(1);
	}
}
