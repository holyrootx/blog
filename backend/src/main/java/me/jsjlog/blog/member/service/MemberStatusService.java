package me.jsjlog.blog.member.service;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.code.ActorTypeCode;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberStatusCode;
import me.jsjlog.blog.member.domain.MemberStatusHistory;
import me.jsjlog.blog.member.domain.MemberStatusReasonCode;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.member.repository.MemberStatusHistoryRepository;
import me.jsjlog.blog.member.repository.MemberStatusRepository;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 상태를 바꾸는 유일한 자리.
 *
 * <p>상태를 바꾸면 같은 트랜잭션에서 이력을 한 줄 남긴다. 다른 곳에서 {@link Member} 의 상태 메서드를
 * 직접 부르면 이력이 빠지므로, 상태 변경은 이 서비스를 거친다.</p>
 *
 * <p>복원·새로 만들기·만료 정리는 같은 탈퇴 회원을 동시에 건드릴 수 있어 조건부 갱신으로 한다.
 * 바뀐 행이 없으면 다른 요청이 먼저 끝난 것이다.</p>
 */
@Service
@RequiredArgsConstructor
public class MemberStatusService {

	private static final String SYSTEM = "system";

	private final MemberRepository memberRepository;
	private final MemberStatusRepository statusRepository;
	private final MemberStatusHistoryRepository historyRepository;
	private final AuditorAware<String> auditorProvider;

	/** 가입. 상태 행은 회원을 저장할 때 함께 생기고, 여기서는 이력만 남긴다 */
	@Transactional
	public void recordSignup(Member member, LocalDateTime now) {
		record(member, null, MemberStatusCode.ACTIVE, MemberStatusReasonCode.SIGNUP,
				ActorTypeCode.SELF, member.getId(), now);
	}

	@Transactional
	public void withdraw(Member member, LocalDateTime now) {
		MemberStatusCode from = member.getStatusCode();
		member.withdraw(now);
		record(member, from, MemberStatusCode.WITHDRAWN, MemberStatusReasonCode.WITHDRAW,
				ActorTypeCode.SELF, member.getId(), now);
	}

	@Transactional
	public void suspend(Member member, Long adminId, LocalDateTime now) {
		MemberStatusCode from = member.getStatusCode();
		member.suspend(now);
		record(member, from, MemberStatusCode.SUSPENDED, MemberStatusReasonCode.SUSPEND,
				ActorTypeCode.ADMIN, adminId, now);
	}

	@Transactional
	public void unsuspend(Member member, Long adminId, LocalDateTime now) {
		MemberStatusCode from = member.getStatusCode();
		member.unsuspend(now);
		record(member, from, MemberStatusCode.ACTIVE, MemberStatusReasonCode.UNSUSPEND,
				ActorTypeCode.ADMIN, adminId, now);
	}

	/**
	 * 복원("예전 계정 쓰기"). 기간 안이고 복원 정보가 그대로일 때만 된다.
	 *
	 * @return 다른 요청이 먼저 복원·새로 만들기를 끝냈거나 기간이 지났으면 {@code false}
	 */
	@Transactional
	public boolean reactivate(Long memberId, AuthProvider provider, String providerUserId,
							  String email, String profileImageUrl, LocalDateTime now) {
		String updatedBy = auditor();
		if (statusRepository.reactivate(memberId, provider, providerUserId, now, updatedBy) != 1) {
			return false;
		}
		// 상태를 이긴 요청만 여기 온다. 회원 행이 이미 다른 값을 들고 있으면 데이터가 어긋난 것이라 되돌린다
		if (memberRepository.restoreProviderLink(memberId, provider, providerUserId, email, profileImageUrl,
				now, updatedBy) != 1) {
			throw new BlogException(ErrorCode.OAUTH_REACTIVATION_NOT_ALLOWED);
		}
		record(memberRepository.getReferenceById(memberId), MemberStatusCode.WITHDRAWN, MemberStatusCode.ACTIVE,
				MemberStatusReasonCode.REACTIVATE, ActorTypeCode.SELF, memberId, now);
		return true;
	}

	/**
	 * "새로 만들기". 예전 회원은 탈퇴 그대로 두고 복원 정보만 지운다. 새 회원 저장은 호출한 쪽이 같은
	 * 트랜잭션에서 한다 — 저장이 실패하면 이 변경도 함께 되돌아가 다시 복원할 수 있다.
	 *
	 * @return 다른 요청이 먼저 끝냈거나 기간이 지났으면 {@code false}
	 */
	@Transactional
	public boolean releaseForRejoin(Long memberId, AuthProvider provider, String providerUserId, LocalDateTime now) {
		if (statusRepository.releaseForRejoin(memberId, provider, providerUserId, now, auditor()) != 1) {
			return false;
		}
		record(memberRepository.getReferenceById(memberId), MemberStatusCode.WITHDRAWN, MemberStatusCode.WITHDRAWN,
				MemberStatusReasonCode.REJOIN, ActorTypeCode.SELF, memberId, now);
		return true;
	}

	@Transactional
	public int expireRestores(LocalDateTime now) {
		int expired = 0;
		for (Long memberId : statusRepository.findExpiredRestoreIds(now)) {
			expired += expire(memberId, now);
		}
		return expired;
	}

	/**
	 * 신규 회원 INSERT 뒤, 가입을 확정하기 전에 최신 복원 정보를 확인한다.
	 * 복원 기간 안이면 사용자의 선택이 필요하므로 가입 전체를 롤백한다.
	 * 기간이 지난 정보만 정리해야 나중에 새 회원이 탈퇴할 때도 복원 키가 겹치지 않는다.
	 */
	@Transactional
	public void prepareForSignup(AuthProvider provider, String providerUserId, LocalDateTime now) {
		statusRepository.findRestoreForUpdate(provider, providerUserId).ifPresent(restore -> {
			if (restore.isRestorable(now)) {
				throw new BlogException(ErrorCode.OAUTH_SIGNUP_NOT_ALLOWED);
			}
			expire(restore.getMemberId(), now);
		});
	}

	private int expire(Long memberId, LocalDateTime now) {
		if (statusRepository.expireRestore(memberId, now, SYSTEM) != 1) {
			return 0;
		}
		record(memberRepository.getReferenceById(memberId), MemberStatusCode.WITHDRAWN, MemberStatusCode.WITHDRAWN,
				MemberStatusReasonCode.RESTORE_EXPIRED, ActorTypeCode.SYSTEM, null, now);
		return 1;
	}

	private void record(Member member, MemberStatusCode from, MemberStatusCode to, MemberStatusReasonCode reason,
						ActorTypeCode actorType, Long actorMemberId, LocalDateTime now) {
		historyRepository.save(new MemberStatusHistory(member, from, to, reason, actorType, actorMemberId, now));
	}

	private String auditor() {
		return auditorProvider.getCurrentAuditor().orElse(SYSTEM);
	}
}
