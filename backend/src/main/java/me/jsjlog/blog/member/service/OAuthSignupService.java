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

	/** 오래 열린 가입·복구 화면도 현재 계정 상태에 맞는 선택지를 받는다. */
	@Transactional(readOnly = true)
	public PendingOAuthSession refreshPending(PendingOAuthSession pendingSession) {
		if (memberRepository.findByProviderAndProviderUserId(pendingSession.provider(),
				pendingSession.providerUserId()).isPresent()) {
			throw new BlogException(ErrorCode.OAUTH_SESSION_EXPIRED);
		}
		var restorable = memberStatusRepository.findRestorable(pendingSession.provider(),
				pendingSession.providerUserId(), LocalDateTime.now());
		return new PendingOAuthSession(pendingSession.provider(), pendingSession.providerUserId(),
				restorable.map(status -> status.getMember().getNickname()).orElse(pendingSession.suggestedNickname()),
				pendingSession.email(), pendingSession.profileImageUrl(),
				restorable.isPresent() ? OAuthLoginState.REACTIVATION_REQUIRED : OAuthLoginState.SIGNUP_REQUIRED);
	}

	@Transactional
	public Member signup(PendingOAuthSession pendingSession, String nickname) {
		// 세션의 loginState는 소셜 인증 당시의 값이다. 현재 DB 상태로 가입 가능 여부를 판단한다.
		boolean alreadyExists = memberRepository
				.findByProviderAndProviderUserId(
						pendingSession.provider(),
						pendingSession.providerUserId()
				)
				.isPresent();

		if (alreadyExists) {
			throw new BlogException(ErrorCode.OAUTH_SIGNUP_NOT_ALLOWED);
		}

		// INSERT의 소셜 식별자 unique 제약으로 동시 가입·복원·탈퇴와 순서를 정한다.
		// 이어지는 잠금 조회는 MySQL REPEATABLE READ에서도 첫 조회 이후의 복원 정보를 읽는다.
		// 복원 선택이 필요하면 이 INSERT와 회원 상태도 같은 트랜잭션에서 롤백된다.
		Member member = memberRepository.saveAndFlush(newMember(pendingSession, nickname));
		LocalDateTime now = LocalDateTime.now();
		memberStatusService.prepareForSignup(pendingSession.provider(), pendingSession.providerUserId(), now);
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
		// 복구·새로 만들기 중 사용자가 요청한 동작만 수행하며, 실제 복원 정보가 있어야 한다.
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
