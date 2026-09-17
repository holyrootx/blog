package me.jsjlog.blog.admin.dto;

import me.jsjlog.blog.admin.domain.UploadImage;

/**
 * 업로드 결과. 편집기가 본문에 넣을 주소를 받는다.
 *
 * storageKey 는 내려보내지 않는다. 화면이 저장소 내부 경로를 알 이유가 없다.
 */
public record UploadImageResponse(Long id, String url, String originalName) {

    public static UploadImageResponse from(UploadImage image) {
        return new UploadImageResponse(image.getId(), image.getUrl(), image.getOriginalName());
    }
}
