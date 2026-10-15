package me.jsjlog.blog.common.upload;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

/** 파일 삭제만 맡는다. 사용 여부, DB 삭제와 실패 처리 정책은 호출자가 정한다. */
@Component
@RequiredArgsConstructor
public class StoredImageFiles {
    private final ImageStorage storage;

    public void delete(String originalKey, String imageUrl) {
        // 접미사를 다시 만들지 않고 저장된 URL을 사용해야 과거 썸네일도 지울 수 있다.
        String thumbnailKey = storageKeyFromUrl(imageUrl);
        storage.delete(originalKey);
        if (!originalKey.equals(thumbnailKey)) storage.delete(thumbnailKey);
    }

    private static String storageKeyFromUrl(String imageUrl) {
        try {
            String path = URI.create(imageUrl).getPath();
            String key = path != null && path.startsWith("/") ? path.substring(1) : path;
            if (key == null || key.isBlank()) throw new IllegalArgumentException();
            return key;
        } catch (IllegalArgumentException exception) {
            throw new BlogException(ErrorCode.IMAGE_DELETE_FAILED, "삭제할 이미지의 저장소 키를 확인할 수 없습니다.");
        }
    }
}
