package me.jsjlog.blog.notification.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.response.ApiResponse;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.notification.dto.NotificationResponse;
import me.jsjlog.blog.notification.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 알림.
 *
 * <p>전부 로그인이 필요하다. {@code /api/v1/auth/me} 와 달리 "아무도 아님" 을 답할 일이 없다 —
 * 로그인하지 않은 사람에게는 알림이라는 개념 자체가 없다.</p>
 */
@RestController
@RequestMapping("/api/v1/auth/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<NotificationResponse>> myNotifications(
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ApiResponse.ok(notificationService.findMine(principal.getId()));
    }

    /**
     * 종에 붙는 숫자.
     *
     * 목록과 따로 둔 이유는 부르는 빈도가 다르기 때문이다. 숫자는 화면을 옮길 때마다
     * 확인하고, 목록은 종을 열었을 때만 필요하다.
     */
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(@AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.ok(notificationService.countUnread(principal.getId()));
    }

    @PutMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long notificationId
    ) {
        notificationService.markRead(notificationId, principal.getId());

        return ApiResponse.ok();
    }

    @PutMapping("/read")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal MemberPrincipal principal) {
        notificationService.markAllRead(principal.getId());

        return ApiResponse.ok();
    }
}
