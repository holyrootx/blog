package me.jsjlog.blog.common.security;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 단일 서버의 인증 세션만 관리한다. 계정 상태와 인증의 원본은 계속 DB다. */
@Service
public class MemberSessionManager {

    private static final String REGISTRATION = MemberSessionManager.class.getName();
    private static final int ADMIN_IDLE_SECONDS = 2 * 60 * 60;

    private final MemberRepository members;
    private final int defaultIdleSeconds;
    private final Map<Long, AccountSessions> accounts = new ConcurrentHashMap<>();

    public MemberSessionManager(MemberRepository members,
                                @Value("${server.servlet.session.timeout:30m}") String defaultTimeout) {
        this.members = members;
        this.defaultIdleSeconds = Math.toIntExact(DurationStyle.detectAndParse(defaultTimeout, ChronoUnit.SECONDS).getSeconds());
    }

    /** 세 로그인 완료 경로가 함께 사용한다. 회원 로그인은 관리자 세션의 2시간을 상속하지 않는다. */
    public void onAuthentication(HttpServletRequest request, MemberPrincipal principal) {
        HttpSession session = request.getSession();
        if (!validateAndRegister(session, principal)) {
            SecurityContextHolder.clearContext();
            throw new SessionAuthenticationException("계정 정보가 변경되었습니다. 다시 로그인해 주세요.");
        }
        int containerMinutes = request.getServletContext().getSessionTimeout();
        int memberTimeout = containerMinutes > 0 ? Math.multiplyExact(containerMinutes, 60) : defaultIdleSeconds;
        try {
            session.setMaxInactiveInterval(principal.isAdmin() ? ADMIN_IDLE_SECONDS : memberTimeout);
        } catch (IllegalStateException revokedDuringLogin) {
            SecurityContextHolder.clearContext();
            throw new SessionAuthenticationException("계정 정보가 변경되었습니다. 다시 로그인해 주세요.");
        }
    }

    /** 다른 제공자 계정의 가입 대기로 전환할 때 세션은 보존하고 이전 회원과의 연결만 끊는다. */
    public void removeRegistration(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        try {
            if (session.getAttribute(REGISTRATION) instanceof Registration registration) {
                // 폐기의 스냅샷 생성과 분리 사이에서 새 가입 대기 세션이 잘못 폐기되지 않게 한다.
                synchronized (registration.account) {
                    if (session.getAttribute(REGISTRATION) == registration) {
                        session.removeAttribute(REGISTRATION);
                    }
                }
            }
        } catch (IllegalStateException alreadyExpired) {
            // 정지·탈퇴가 먼저 완료됐다면 호출자는 새로운 가입 대기 세션을 만들 수 있다.
        }
    }

    /** 등록을 빠뜨린 인증 경로도 첫 요청에서 확인한다. 프로필 변경은 인증 변경으로 보지 않는다. */
    public boolean validateAndRegister(HttpSession session, MemberPrincipal principal) {
        if (session == null) {
            return false;
        }
        AccountSessions account = accounts.computeIfAbsent(principal.getId(), ignored -> new AccountSessions());
        synchronized (account) {
            Member current = members.findById(principal.getId()).orElse(null);
            // 옛 비밀번호의 요청이 새 비밀번호로 로그인한 정상 세션까지 폐기하면 안 된다.
            for (Registration registration : List.copyOf(account.registrations)) {
                if (!registration.principal.matchesCurrentAccount(current)) {
                    invalidate(registration.session);
                }
            }
            if (!principal.matchesCurrentAccount(current)
                    || (account.revokedAtNanos != null && principal.issuedAtNanos() - account.revokedAtNanos <= 0)) {
                invalidate(session);
                return false;
            }
            try {
                Object existing = session.getAttribute(REGISTRATION);
                if (!(existing instanceof Registration existingRegistration) || existingRegistration.principal != principal) {
                    Registration registration = new Registration(account, session, principal);
                    account.registrations.add(registration);
                    try {
                        session.setAttribute(REGISTRATION, registration);
                    } catch (IllegalStateException expired) {
                        account.registrations.remove(registration);
                        return false;
                    }
                }
                return true;
            } catch (IllegalStateException expired) {
                return false;
            }
        }
    }

    /** 상태 변경이 롤백되면 세션은 유지한다. 복구 뒤에도 폐기 이전의 인증은 다시 등록할 수 없다. */
    public void revokeAfterCommit(Long memberId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("세션 폐기는 계정 변경 트랜잭션 안에서 예약해야 합니다.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                AccountSessions account = accounts.computeIfAbsent(memberId, ignored -> new AccountSessions());
                synchronized (account) {
                    account.revokedAtNanos = System.nanoTime();
                    for (Registration registration : List.copyOf(account.registrations)) {
                        invalidate(registration.session);
                    }
                }
            }
        });
    }

    private static void invalidate(HttpSession session) {
        try {
            session.invalidate();
        } catch (IllegalStateException alreadyExpired) {
            // 다른 요청이나 컨테이너의 만료 처리가 먼저 끝났어도 폐기 결과는 같다.
        }
    }

    private static final class AccountSessions {
        private final Set<Registration> registrations = ConcurrentHashMap.newKeySet();
        private Long revokedAtNanos;
    }

    /** 컨테이너 만료·로그아웃 시 참조를 제거한다. 세션 ID 교체에도 같은 세션 객체를 추적한다. */
    private static final class Registration implements HttpSessionBindingListener {
        private final AccountSessions account;
        private final HttpSession session;
        private final MemberPrincipal principal;

        private Registration(AccountSessions account, HttpSession session, MemberPrincipal principal) {
            this.account = account;
            this.session = session;
            this.principal = principal;
        }

        @Override
        public void valueUnbound(HttpSessionBindingEvent event) {
            // 컨테이너가 세션 잠금을 잡은 상태에서도 호출한다. 여기서 계정 잠금을 잡지 않는다.
            account.registrations.remove(this);
        }
    }
}
