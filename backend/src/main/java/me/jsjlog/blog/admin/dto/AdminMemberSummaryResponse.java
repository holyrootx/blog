package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.MemberStatus;

public record AdminMemberSummaryResponse(
        Long id,
        String nickname,
        String email,
        AuthProvider provider,
        MemberStatus status,
        long commentCount,
        LocalDateTime createdAt
) {
}
