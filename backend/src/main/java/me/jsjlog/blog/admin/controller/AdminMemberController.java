package me.jsjlog.blog.admin.controller;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminMemberListResponse;
import me.jsjlog.blog.admin.dto.AdminMemberSearchCondition;
import me.jsjlog.blog.admin.service.AdminMemberService;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.MemberPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/blog/members")
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @GetMapping
    public ApiResponse<AdminMemberListResponse> getMembers(AdminMemberSearchCondition condition) {
        return ApiResponse.ok(adminMemberService.getMembers(condition));
    }

    @PostMapping("/{memberId}/suspend")
    public ApiResponse<Void> suspend(@PathVariable Long memberId,
                                     @AuthenticationPrincipal MemberPrincipal principal) {
        adminMemberService.suspend(memberId, principal.getId());
        return ApiResponse.ok();
    }

    @PostMapping("/{memberId}/unsuspend")
    public ApiResponse<Void> unsuspend(@PathVariable Long memberId,
                                       @AuthenticationPrincipal MemberPrincipal principal) {
        adminMemberService.unsuspend(memberId, principal.getId());
        return ApiResponse.ok();
    }
}
