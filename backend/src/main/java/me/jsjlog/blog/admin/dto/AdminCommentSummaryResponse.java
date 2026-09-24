package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.member.domain.MemberRole;

import java.time.LocalDateTime;

public record AdminCommentSummaryResponse(
        Long id,
        Long postId,
        String postTitle,
        Long parentId,
        String nickname,
        MemberRole memberRole,
        String content,
        LocalDateTime createdAt,
        boolean hidden,
        boolean answered,

        /**
         * 이 댓글이 신고된 횟수.
         *
         * <p>숫자가 크다고 저절로 숨기지 않는다. 여럿이 몰려 신고하면 멀쩡한 댓글이
         * 사라지고, 그러면 신고가 도리어 공격 수단이 된다. 여기 보이는 것은
         * "먼저 읽어 볼 것" 이라는 표시일 뿐 판단은 사람이 한다.</p>
         *
         * <p>누가 신고했는지는 내보내지 않는다. 운영자가 신고자를 알면 그다음 댓글을
         * 읽는 눈이 달라지고, 신고한 사람도 그걸 알면 신고를 망설인다.</p>
         */
        long reportCount,

        /**
         * 아직 판단하지 않은 신고 수.
         *
         * <p>전체 신고 수와 나눠 두는 이유는 "봐야 할 것" 과 "이미 본 것" 이 다르기
         * 때문이다. 전체만 있으면 처리해도 숫자가 그대로라 목록에서 계속 눈에 걸린다.</p>
         */
        long unhandledReportCount
) {
}
