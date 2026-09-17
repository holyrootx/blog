package me.jsjlog.blog.admin.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import me.jsjlog.blog.admin.domain.UploadImage;
import me.jsjlog.blog.admin.dto.UploadImageResponse;
import me.jsjlog.blog.admin.repository.UploadImageRepository;
import me.jsjlog.blog.common.config.UploadProperties;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.upload.ImageStorage;
import me.jsjlog.blog.common.upload.StoredImage;

/**
 * 이미지 업로드와 삭제.
 *
 * 올릴 때도 지울 때도 저장소가 먼저고 기록이 나중이다. 기준은 하나다 —
 * 중간에 실패했을 때 "기록 없는 파일"이 남지 않게 한다. 기록이 없으면 찾을 방법이 없다.
 */
@Service
@RequiredArgsConstructor
public class AdminImageService {

    private static final DateTimeFormatter KEY_PREFIX = DateTimeFormatter.ofPattern("yyyy/MM");

    /** 확장자는 파일 이름이 아니라 실제 형식에서 정한다 */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    private final ImageStorage imageStorage;
    private final UploadImageRepository uploadImageRepository;
    private final UploadProperties uploadProperties;

    @Transactional
    public UploadImageResponse upload(MultipartFile file) {
        String contentType = normalizeContentType(file);
        validate(file, contentType);

        StoredImage stored = store(file, contentType);

        UploadImage image = new UploadImage(
                stored.storageKey(),
                stored.url(),
                originalName(file),
                contentType,
                file.getSize()
        );

        return UploadImageResponse.from(uploadImageRepository.save(image));
    }

    /**
     * 저장소에서 먼저 지우고 기록을 지운다.
     *
     * 순서를 뒤집으면 기록이 먼저 사라진 뒤 파일 삭제가 실패했을 때, 그 파일을 찾을 방법이 없어진다.
     * 이 순서라면 중간에 실패해도 다시 누르면 된다 — 저장소 삭제는 이미 없는 키에도 실패하지 않는다.
     */
    @Transactional
    public void delete(Long imageId) {
        UploadImage image = uploadImageRepository.findById(imageId)
                .orElseThrow(() -> new BlogException(ErrorCode.IMAGE_NOT_FOUND));

        imageStorage.delete(image.getStorageKey());
        uploadImageRepository.delete(image);
    }

    private void validate(MultipartFile file, String contentType) {
        if (file.isEmpty()) {
            throw new BlogException(ErrorCode.IMAGE_EMPTY);
        }

        if (file.getSize() > uploadProperties.maxSizeBytes()) {
            throw new BlogException(ErrorCode.IMAGE_TOO_LARGE);
        }

        if (!uploadProperties.allows(contentType)) {
            throw new BlogException(ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
        }
    }

    private StoredImage store(MultipartFile file, String contentType) {
        try (InputStream content = file.getInputStream()) {
            return imageStorage.store(storageKey(contentType), contentType, file.getSize(), content);
        } catch (IOException exception) {
            throw new BlogException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    /**
     * 저장 이름은 서버가 정한다.
     *
     * 원본 이름을 그대로 쓰면 공백·한글·중복·경로 조각(../)이 전부 따라온다.
     * 날짜로 나누는 것은 한 폴더에 파일이 수만 개 쌓이는 것을 막기 위해서다.
     */
    private String storageKey(String contentType) {
        return "%s/%s.%s".formatted(
                LocalDate.now().format(KEY_PREFIX),
                UUID.randomUUID(),
                EXTENSIONS.getOrDefault(contentType, "bin")
        );
    }

    private String normalizeContentType(MultipartFile file) {
        String contentType = file.getContentType();

        return contentType == null ? "" : contentType.toLowerCase();
    }

    /**
     * 목록에서 알아볼 이름. 경로에는 쓰지 않지만 길이는 컬럼에 맞춰 자른다.
     * 이름이 없는 붙여넣기(클립보드 이미지)도 있으므로 빈 값을 대비한다.
     */
    private String originalName(MultipartFile file) {
        String name = file.getOriginalFilename();

        if (name == null || name.isBlank()) {
            return "이름 없는 이미지";
        }

        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
