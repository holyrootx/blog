package me.jsjlog.blog.member.service;

import java.util.List;
import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.security.MemberSessionManager;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.dto.MyCommentResponse;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.AuditorAware;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 회원이 자기 계정에 대해 하는 일.
 *
 * <p>가입과 재가입은 {@code OAuthSignupService} 가 맡는다. 거기는 아직 회원이 아닌 사람이
 * 지나가는 길이고, 여기는 이미 회원인 사람이 자기 것을 고치는 자리라 나눠 둔다.</p>
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    /**
     * 한 번에 내려주는 댓글 수.
     *
     * 개인 블로그라 한 사람이 남기는 댓글이 많지 않다. 더 필요해지면 그때 페이지를 붙인다 —
     * 지금 커서를 만들어 두면 쓰지 않는 코드가 남는다.
     */
    private static final int MY_COMMENT_LIMIT = 100;

    private final MemberRepository memberRepository;
    private final MemberSessionManager memberSessions;
    private final CommentRepository commentRepository;
    private final AuditorAware<String> auditorProvider;

    @Transactional
    public Member changeNickname(Long memberId, String nickname) {
        Member member = requiredActiveMember(memberId);
        String trimmedNickname = nickname.trim();
        if (memberRepository.updateActiveNickname(memberId, trimmedNickname, LocalDateTime.now(),
                auditorProvider.getCurrentAuditor().orElse("system")) != 1) {
            throw new BlogException(ErrorCode.FORBIDDEN);
        }
        // 조건부 부분 갱신 이후 분리된 객체다. 응답 값만 맞추고 오래된 상태는 저장하지 않는다.
        member.changeNickname(trimmedNickname);

        return member;
    }

    /**
     * 탈퇴.
     *
     * <p>행을 지우지 않는다. 댓글이 이 행을 가리키고 있어서 지우면 댓글이 사라지고
     * 답글이 부모를 잃는다. 무엇을 남기고 무엇을 지우는지는 {@link Member#withdraw()} 에 있다.</p>
     *
     * <p>탈퇴가 커밋된 뒤 다른 브라우저의 인증도 함께 폐기한다.</p>
     */
    @Transactional
    public void withdraw(Long memberId) {
        Member member = requiredMember(memberId);

        // 관리자가 탈퇴하면 블로그에 들어갈 사람이 없어진다. 되돌리려면 DB 를 직접 고쳐야 한다
        if (member.isAdmin()) {
            throw new BlogException(ErrorCode.MEMBER_ADMIN_CANNOT_WITHDRAW);
        }

        if (!member.getStatus().isActive()) {
            throw new BlogException(ErrorCode.FORBIDDEN);
        }

        member.withdraw();
        memberSessions.revokeAfterCommit(memberId);
    }

    @Transactional(readOnly = true)
    public List<MyCommentResponse> findMyComments(Long memberId) {
        return commentRepository
                .findMyComments(memberId, PageRequest.of(0, MY_COMMENT_LIMIT))
                .stream()
                .map(MyCommentResponse::from)
                .toList();
    }

    private Member requiredMember(Long memberId) {
        return memberRepository
                .findById(memberId)
                .orElseThrow(() -> new BlogException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Member requiredActiveMember(Long memberId) {
        Member member = requiredMember(memberId);

        if (!member.getStatus().isActive()) {
            throw new BlogException(ErrorCode.FORBIDDEN);
        }

        return member;
    }
}
