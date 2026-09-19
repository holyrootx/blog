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
        boolean answered
) {
}
