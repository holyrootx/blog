package me.jsjlog.blog.admin.service;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminMemberListResponse;
import me.jsjlog.blog.admin.dto.AdminMemberSearchCondition;
import me.jsjlog.blog.admin.dto.AdminMemberSummaryResponse;
import me.jsjlog.blog.admin.repository.AdminMemberQueryRepository;
import me.jsjlog.blog.common.code.CommonCodes;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.security.MemberSessionManager;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberStatusCode;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.member.service.MemberStatusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final MemberSessionManager memberSessions;
    private final AdminMemberQueryRepository memberQueryRepository;
    private final MemberStatusService memberStatusService;
    private final CommonCodes commonCodes;

    @Transactional(readOnly = true)
    public AdminMemberListResponse getMembers(AdminMemberSearchCondition condition) {
        long total = memberQueryRepository.countMembers(condition);
        int size = condition.sizeOrDefault();

        return new AdminMemberListResponse(
                memberQueryRepository.getMembers(condition).stream().map(this::withStatusName).toList(),
                memberQueryRepository.countByStatus(condition),
                condition.pageOrDefault(),
                size,
                total,
                total == 0 ? 0 : (int) Math.ceil((double) total / size)
        );
    }

    @Transactional
    public void suspend(Long memberId, Long adminId) {
        Member member = findMember(memberId);
        requireModeratable(member);

        if (member.getStatusCode() == MemberStatusCode.SUSPENDED) {
            return;
        }
        if (member.getStatusCode() != MemberStatusCode.ACTIVE) {
            throw new BlogException(ErrorCode.MEMBER_STATUS_CHANGE_NOT_ALLOWED);
        }

        memberStatusService.suspend(member, adminId, LocalDateTime.now());
        memberSessions.revokeAfterCommit(memberId);
    }

    @Transactional
    public void unsuspend(Long memberId, Long adminId) {
        Member member = findMember(memberId);
        requireModeratable(member);

        if (member.getStatusCode() == MemberStatusCode.ACTIVE) {
            return;
        }
        if (member.getStatusCode() != MemberStatusCode.SUSPENDED) {
            throw new BlogException(ErrorCode.MEMBER_STATUS_CHANGE_NOT_ALLOWED);
        }

        memberStatusService.unsuspend(member, adminId, LocalDateTime.now());
    }

    /** 상태 이름은 공통 코드 MEMBER_STATUS 에서 가져온다. 관리 화면에서 이름을 고치면 다음 조회부터 바뀐다 */
    private AdminMemberSummaryResponse withStatusName(AdminMemberSummaryResponse row) {
        return row.withStatusName(commonCodes.nameOf(row.status()));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BlogException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void requireModeratable(Member member) {
        if (member.isAdmin()) {
            throw new BlogException(ErrorCode.MEMBER_ADMIN_CANNOT_MODERATE);
        }
    }
}
