package me.jsjlog.blog.member.service;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.security.oauth.OAuthLoginState;
import me.jsjlog.blog.common.security.oauth.PendingOAuthSession;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberStatus;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.member.repository.MemberStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuthSignupService {

    private final MemberRepository memberRepository;
    private final MemberStatusRepository memberStatusRepository;
    private final MemberStatusService memberStatusService;

    @Transactional
    public Member signup(PendingOAuthSession pendingSession, String nickname) {
        if (pendingSession.loginState() != OAuthLoginState.SIGNUP_REQUIRED) {
            throw new BlogException(ErrorCode.OAUTH_SIGNUP_NOT_ALLOWED);
        }

        boolean alreadyExists = memberRepository
                .findByProviderAndProviderUserId(
                        pendingSession.provider(),
                        pendingSession.providerUserId()
                )
                .isPresent();

        if (alreadyExists) {
            throw new BlogException(ErrorCode.OAUTH_SIGNUP_NOT_ALLOWED);
        }

        LocalDateTime now = LocalDateTime.now();
        memberStatusService.expireStaleRestore(pendingSession.provider(), pendingSession.providerUserId(), now);
        Member member = memberRepository.save(newMember(pendingSession, nickname));
        memberStatusService.recordSignup(member, now);
        return member;
    }

    @Transactional
    public Member reactivate(PendingOAuthSession pendingSession) {
        LocalDateTime now = LocalDateTime.now();
        Long memberId = requiredRestorable(pendingSession, now).getMemberId();

        if (!memberStatusService.reactivate(memberId, pendingSession.provider(), pendingSession.providerUserId(),
                pendingSession.email(), pendingSession.profileImageUrl(), now)) {
            throw new BlogException(ErrorCode.OAUTH_REACTIVATION_NOT_ALLOWED);
        }
        // 조건부 갱신이 영속성 컨텍스트를 비웠으므로 응답에 쓸 최신 행을 다시 읽는다
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BlogException(ErrorCode.OAUTH_REACTIVATION_NOT_ALLOWED));
    }

    @Transactional
    public Member rejoin(PendingOAuthSession pendingSession, String nickname) {
        LocalDateTime now = LocalDateTime.now();
        Long withdrawnId = requiredRestorable(pendingSession, now).getMemberId();

        // 복원 정보가 그대로인 경우에만 새 계정을 만든다. 정보를 먼저 지우되 새 회원 저장이 실패하면
        // 이 변경도 함께 롤백되어 다시 복원할 수 있다
        if (!memberStatusService.releaseForRejoin(withdrawnId, pendingSession.provider(),
                pendingSession.providerUserId(), now)) {
            throw new BlogException(ErrorCode.OAUTH_REACTIVATION_NOT_ALLOWED);
        }

        Member member = memberRepository.save(newMember(pendingSession, nickname));
        memberStatusService.recordSignup(member, now);
        return member;
    }

    private MemberStatus requiredRestorable(PendingOAuthSession pendingSession, LocalDateTime now) {
        if (pendingSession.loginState() != OAuthLoginState.REACTIVATION_REQUIRED) {
            throw new BlogException(ErrorCode.OAUTH_REACTIVATION_NOT_ALLOWED);
        }

        return memberStatusRepository
                .findRestorable(pendingSession.provider(), pendingSession.providerUserId(), now)
                .orElseThrow(() -> new BlogException(ErrorCode.OAUTH_REACTIVATION_NOT_ALLOWED));
    }

    private static Member newMember(PendingOAuthSession pendingSession, String nickname) {
        return Member.ofSocial(
                pendingSession.provider(),
                pendingSession.providerUserId(),
                nickname.trim(),
                pendingSession.email(),
                pendingSession.profileImageUrl()
        );
    }
}
