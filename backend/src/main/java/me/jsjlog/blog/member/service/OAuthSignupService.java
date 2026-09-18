package me.jsjlog.blog.member.service;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.security.oauth.OAuthLoginState;
import me.jsjlog.blog.common.security.oauth.PendingOAuthSession;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberStatus;
import me.jsjlog.blog.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuthSignupService {

    private final MemberRepository memberRepository;

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

        withdrawnMember.reactivate(
                pendingSession.email(),
                pendingSession.profileImageUrl()
        );

        return withdrawnMember;
    }

    @Transactional
    public Member rejoin(PendingOAuthSession pendingSession, String nickname) {
        Member withdrawnMember = requiredWithdrawnMember(pendingSession);

        // Hibernate 는 INSERT 를 UPDATE 보다 먼저 실행할 수 있다. 기존 연결을 flush 하지 않고
        // 새 회원을 넣으면 (provider, provider_user_id) unique 제약에 먼저 걸린다.
        withdrawnMember.detachProvider();
        memberRepository.saveAndFlush(withdrawnMember);

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
