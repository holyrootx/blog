package me.jsjlog.blog.admin.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminCommonCodeCreateRequest;
import me.jsjlog.blog.admin.dto.AdminCommonCodeGroupCreateRequest;
import me.jsjlog.blog.admin.dto.AdminCommonCodeGroupListResponse;
import me.jsjlog.blog.admin.dto.AdminCommonCodeGroupSearchCondition;
import me.jsjlog.blog.admin.dto.AdminCommonCodeGroupSummaryResponse;
import me.jsjlog.blog.admin.dto.AdminCommonCodeGroupUpdateRequest;
import me.jsjlog.blog.admin.dto.AdminCommonCodeResponse;
import me.jsjlog.blog.admin.dto.AdminCommonCodeUpdateRequest;
import me.jsjlog.blog.admin.service.AdminCommonCodeService;
import me.jsjlog.blog.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/common-codes/groups")
public class AdminCommonCodeController {

    private final AdminCommonCodeService adminCommonCodeService;

    @GetMapping
    public ApiResponse<AdminCommonCodeGroupListResponse> getGroups(AdminCommonCodeGroupSearchCondition condition) {
        return ApiResponse.ok(adminCommonCodeService.getGroups(condition));
    }

    @PostMapping
    public ApiResponse<AdminCommonCodeGroupSummaryResponse> createGroup(
            @RequestBody AdminCommonCodeGroupCreateRequest request) {
        return ApiResponse.ok(adminCommonCodeService.createGroup(request));
    }

    @PutMapping("/{groupCode}")
    public ApiResponse<AdminCommonCodeGroupSummaryResponse> updateGroup(
            @PathVariable String groupCode, @RequestBody AdminCommonCodeGroupUpdateRequest request) {
        return ApiResponse.ok(adminCommonCodeService.updateGroup(groupCode, request));
    }

    @GetMapping("/{groupCode}/codes")
    public ApiResponse<List<AdminCommonCodeResponse>> getCodes(@PathVariable String groupCode) {
        return ApiResponse.ok(adminCommonCodeService.getCodes(groupCode));
    }

    @PostMapping("/{groupCode}/codes")
    public ApiResponse<AdminCommonCodeResponse> createCode(
            @PathVariable String groupCode, @RequestBody AdminCommonCodeCreateRequest request) {
        return ApiResponse.ok(adminCommonCodeService.createCode(groupCode, request));
    }

    @PutMapping("/{groupCode}/codes/{code}")
    public ApiResponse<AdminCommonCodeResponse> updateCode(
            @PathVariable String groupCode, @PathVariable String code,
            @RequestBody AdminCommonCodeUpdateRequest request) {
        return ApiResponse.ok(adminCommonCodeService.updateCode(groupCode, code, request));
    }
}
