package me.jsjlog.blog.common.upload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import lombok.RequiredArgsConstructor;

import me.jsjlog.blog.common.config.UploadProperties;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;

/**
 * 디스크에 그대로 두는 저장소. 개발용이다.
 *
 * 운영은 R2 를 쓴다. 서버 디스크에 두면 배포할 때마다 파일이 흩어지고 백업 대상이 하나 늘어난다.
 */
@RequiredArgsConstructor
public class LocalImageStorage implements ImageStorage {

    private final UploadProperties properties;

    @Override
    public StoredImage store(String storageKey, String contentType, long size, InputStream content) {
        Path target = resolve(storageKey);

        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BlogException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }

        return new StoredImage(storageKey, properties.publicPath() + "/" + storageKey);
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException exception) {
            throw new BlogException(ErrorCode.IMAGE_DELETE_FAILED);
        }
    }

    /**
     * 저장 경로를 벗어나지 못하게 막는다.
     *
     * 키는 서버가 만들지만, 언젠가 밖에서 온 값이 여기로 흘러들면
     * {@code ../../} 같은 조각 하나로 아무 파일이나 덮어쓸 수 있다.
     */
    private Path resolve(String storageKey) {
        Path root = Path.of(properties.localDirectory()).toAbsolutePath().normalize();
        Path target = root.resolve(storageKey).normalize();

        if (!target.startsWith(root)) {
            throw new BlogException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }

        return target;
    }
}
