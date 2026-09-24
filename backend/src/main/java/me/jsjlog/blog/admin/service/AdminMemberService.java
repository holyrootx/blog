package me.jsjlog.blog.admin.service;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminMemberListResponse;
import me.jsjlog.blog.admin.dto.AdminMemberSearchCondition;
import me.jsjlog.blog.admin.repository.AdminMemberQueryRepository;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberStatus;
import me.jsjlog.blog.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final AdminMemberQueryRepository memberQueryRepository;

    @Transactional(readOnly = true)
    public AdminMemberListResponse getMembers(AdminMemberSearchCondition condition) {
        long total = memberQueryRepository.countMembers(condition);
        int size = condition.sizeOrDefault();

        return new AdminMemberListResponse(
                memberQueryRepository.getMembers(condition),
                memberQueryRepository.countByStatus(condition),
                condition.pageOrDefault(),
                size,
                total,
                total == 0 ? 0 : (int) Math.ceil((double) total / size)
        );
    }

    @Transactional
    public void suspend(Long memberId) {
        Member member = findMember(memberId);
        requireModeratable(member);

        if (member.getStatus() == MemberStatus.SUSPENDED) {
            return;
        }
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BlogException(ErrorCode.MEMBER_STATUS_CHANGE_NOT_ALLOWED);
        }

        member.suspend();
    }

    @Transactional
    public void unsuspend(Long memberId) {
        Member member = findMember(memberId);
        requireModeratable(member);

        if (member.getStatus() == MemberStatus.ACTIVE) {
            return;
        }
        if (member.getStatus() != MemberStatus.SUSPENDED) {
            throw new BlogException(ErrorCode.MEMBER_STATUS_CHANGE_NOT_ALLOWED);
        }

        member.unsuspend();
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
