package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.post.domain.DeviceType;

public record AdminDeviceViewResponse(
        DeviceType deviceType,
        long pageViews
) {
}
