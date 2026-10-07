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
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuthSignupService {

    private final MemberRepository memberRepository;
    private final AuditorAware<String> auditorProvider;

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

        Member member = Member.ofSocial(
                pendingSession.provider(),
                pendingSession.providerUserId(),
                nickname.trim(),
                pendingSession.email(),
                pendingSession.profileImageUrl()
        );

        return memberRepository.save(member);
    }

    @Transactional
    public Member reactivate(PendingOAuthSession pendingSession) {
        Member withdrawnMember = requiredWithdrawnMember(pendingSession);

        if (memberRepository.reactivateWithdrawn(withdrawnMember.getId(), pendingSession.provider(),
                pendingSession.providerUserId(), pendingSession.email(), pendingSession.profileImageUrl(),
                LocalDateTime.now(), auditorProvider.getCurrentAuditor().orElse("system")) != 1) {
            throw new BlogException(ErrorCode.OAUTH_REACTIVATION_NOT_ALLOWED);
        }
        // 조건부 갱신이 영속성 컨텍스트를 비웠으므로 응답용 객체만 맞춘다.
        withdrawnMember.reactivate(
                pendingSession.email(),
                pendingSession.profileImageUrl()
        );

        return withdrawnMember;
    }

    @Transactional
    public Member rejoin(PendingOAuthSession pendingSession, String nickname) {
        Member withdrawnMember = requiredWithdrawnMember(pendingSession);

        // UPDATE 시점에도 탈퇴 상태와 제공자 연결이 그대로인 경우에만 새 계정을 만든다.
        // 연결을 먼저 해제하되 새 회원 저장이 실패하면 이 변경도 함께 롤백한다.
        if (memberRepository.detachWithdrawnProvider(withdrawnMember.getId(), pendingSession.provider(),
                pendingSession.providerUserId(), LocalDateTime.now(),
                auditorProvider.getCurrentAuditor().orElse("system")) != 1) {
            throw new BlogException(ErrorCode.OAUTH_REACTIVATION_NOT_ALLOWED);
        }

        Member newMember = Member.ofSocial(
                pendingSession.provider(),
                pendingSession.providerUserId(),
                nickname.trim(),
                pendingSession.email(),
                pendingSession.profileImageUrl()
        );

        return memberRepository.save(newMember);
    }

    private Member requiredWithdrawnMember(PendingOAuthSession pendingSession) {
        if (pendingSession.loginState() != OAuthLoginState.REACTIVATION_REQUIRED) {
            throw new BlogException(ErrorCode.OAUTH_REACTIVATION_NOT_ALLOWED);
        }

        Member member = memberRepository
                .findByProviderAndProviderUserId(
                        pendingSession.provider(),
                        pendingSession.providerUserId()
                )
                .orElseThrow(() -> new BlogException(ErrorCode.OAUTH_REACTIVATION_NOT_ALLOWED));

        if (member.getStatus() != MemberStatus.WITHDRAWN) {
            throw new BlogException(ErrorCode.OAUTH_REACTIVATION_NOT_ALLOWED);
        }

        return member;
    }
}
