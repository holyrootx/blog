package me.jsjlog.blog.guestbook.dto;

import java.time.LocalDateTime;

import me.jsjlog.blog.guestbook.domain.GuestbookEntry;
import me.jsjlog.blog.member.domain.MemberRole;

/**
 * 공개 방명록 한 줄.
 *
 * <p>비밀글을 읽을 수 없는 사람에게는 본문과 답글을 비워서 준다. 화면에서 가리면
 * 응답에는 그대로 실려 나가 누구나 개발자 도구로 읽을 수 있다.</p>
 *
 * <p>다른 회원의 프로필 사진 주소는 싣지 않는다. 댓글 목록처럼 닉네임 첫 글자로 그린다.</p>
 */
public record GuestbookEntryResponse(
        Long id,
        String nickname,
        /** 운영자가 쓴 글. 화면이 "운영자" 표시를 붙인다 */
        boolean writtenByAdmin,
        /** 읽을 수 없는 비밀글이면 null */
        String content,
        boolean secret,
        boolean readable,
        boolean mine,
        boolean edited,
        /** 운영자가 가린 내 글. 목록에서 나에게만 남는다 */
        boolean hiddenByAdmin,
        boolean reportedByMe,
        LocalDateTime createdAt,
        /** 답글이 없거나 읽을 수 없으면 null */
        GuestbookReplyResponse reply
) {

    public static GuestbookEntryResponse of(
            GuestbookEntry entry,
            Long viewerId,
            boolean viewerIsAdmin,
            boolean reportedByMe
    ) {
        boolean mine = entry.isWrittenBy(viewerId);
        boolean readable = !entry.isSecret() || mine || viewerIsAdmin;
        // 운영자가 가린 글은 쓴 사람에게 자리만 남기고 본문과 답글은 싣지 않는다. 가린 동안 내용은
        // 관리자만 본다(개인정보 처리방침). 댓글도 가린 본문은 쓴 사람 목록에서 비워 둔다
        boolean hidden = entry.isDeleted() && entry.isHiddenByAdmin();
        boolean showContent = readable && !hidden;

        return new GuestbookEntryResponse(
                entry.getId(),
                entry.getMember().getNickname(),
                entry.getMember().getRole() == MemberRole.ADMIN,
                showContent ? entry.getContent() : null,
                entry.isSecret(),
                readable,
                mine,
                entry.isEdited(),
                hidden,
                reportedByMe,
                entry.getCreatedAt(),
                showContent && entry.hasReply() ? reply(entry) : null
        );
    }

    private static GuestbookReplyResponse reply(GuestbookEntry entry) {
        return new GuestbookReplyResponse(
                entry.getReplyMember() == null ? null : entry.getReplyMember().getNickname(),
                entry.getReplyContent(),
                entry.getRepliedAt()
        );
    }
}
