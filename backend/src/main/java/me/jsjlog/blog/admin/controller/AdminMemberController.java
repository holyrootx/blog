package me.jsjlog.blog.admin.controller;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminMemberListResponse;
import me.jsjlog.blog.admin.dto.AdminMemberSearchCondition;
import me.jsjlog.blog.admin.service.AdminMemberService;
import me.jsjlog.blog.common.response.ApiResponse;
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
    public ApiResponse<Void> suspend(@PathVariable Long memberId) {
        adminMemberService.suspend(memberId);
        return ApiResponse.ok();
    }

    @PostMapping("/{memberId}/unsuspend")
    public ApiResponse<Void> unsuspend(@PathVariable Long memberId) {
        adminMemberService.unsuspend(memberId);
        return ApiResponse.ok();
    }
}
