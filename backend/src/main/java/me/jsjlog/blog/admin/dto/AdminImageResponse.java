package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

import me.jsjlog.blog.admin.domain.UploadImage;

public record AdminImageResponse(
        Long id,
        String url,
        String originalName,
        String contentType,
        long byteSize,
        LocalDateTime uploadedAt,
        /** 비어 있으면 아무 데서도 안 쓰는 이미지다 */
        List<AdminImageUsage> usages
) {

    public static AdminImageResponse of(UploadImage image, List<AdminImageUsage> usages) {
        return new AdminImageResponse(
                image.getId(),
                image.getUrl(),
                image.getOriginalName(),
                image.getContentType(),
                image.getByteSize(),
                image.getCreatedAt(),
                usages
        );
    }
}
