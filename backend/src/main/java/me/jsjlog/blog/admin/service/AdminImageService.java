package me.jsjlog.blog.admin.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import me.jsjlog.blog.admin.domain.UploadImage;
import me.jsjlog.blog.admin.dto.UploadImageResponse;
import me.jsjlog.blog.admin.repository.UploadImageRepository;
import me.jsjlog.blog.common.config.UploadProperties;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.upload.ImageStorage;
import me.jsjlog.blog.common.upload.ImageThumbnail;
import me.jsjlog.blog.common.upload.StoredImage;
import me.jsjlog.blog.common.upload.ThumbnailGenerator;

/**
 * 이미지 업로드와 삭제.
 *
 * 올릴 때도 지울 때도 저장소가 먼저고 기록이 나중이다. 기준은 하나다 —
 * 중간에 실패했을 때 "기록 없는 파일"이 남지 않게 한다. 기록이 없으면 찾을 방법이 없다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminImageService {

    private static final DateTimeFormatter KEY_PREFIX = DateTimeFormatter.ofPattern("yyyy/MM");

    /** 확장자는 파일 이름이 아니라 실제 형식에서 정한다 */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    /**
     * 썸네일 키는 원본 키에서 만든다.
     *
     * <p>컬럼을 따로 두지 않는 이유다 — 원본 키만 알면 썸네일 키가 나오고, 반대도 된다.
     * <b>폭이나 품질을 키에 넣지 않는 것이 중요하다.</b> {@code -w1800} 처럼 적어 두면
     * 나중에 폭을 바꿨을 때 옛 파일을 규칙으로 못 찾아 저장소에 고아로 남는다.
     * 이름을 고정해 두면 같은 자리에 덮어쓰므로 그런 일이 없다.</p>
     */
    private static final String THUMBNAIL_SUFFIX = "-thumbnail-v1.0.webp";

    private final ImageStorage imageStorage;
    private final UploadImageRepository uploadImageRepository;
    private final UploadProperties uploadProperties;
    private final ThumbnailGenerator thumbnailGenerator;
    private final PlatformTransactionManager transactionManager;

    /**
     * 원본은 그대로 두고, 화면에 내보낼 썸네일을 따로 만들어 올린다.
     *
     * <p>본문에 박히는 주소는 <b>썸네일</b>이다. 원본은 보관용이다 — 나중에 폭이나 품질을
     * 다시 정하게 되면 원본이 있어야 다시 뽑는다. 원본을 버리는 것은 되돌릴 수 없다.</p>
     */
    public UploadImageResponse upload(MultipartFile file) {
        String contentType = normalizeContentType(file);
        validate(file, contentType);

        byte[] original = read(file);
        ImageThumbnail thumbnail = thumbnailGenerator.generate(original, contentType);
        String originalKey = storageKey(contentType);
        String thumbnailKey = thumbnailKey(originalKey);
        List<String> attemptedKeys = new ArrayList<>(2);

        try {
            attemptedKeys.add(originalKey);
            StoredImage storedOriginal = store(originalKey, contentType, original);

            attemptedKeys.add(thumbnailKey);
            StoredImage storedThumbnail = store(
                    thumbnailKey,
                    thumbnail.contentType(),
                    thumbnail.content()
            );

            UploadImage image = new UploadImage(
                    storedOriginal.storageKey(),
                    storedThumbnail.url(),
                    originalName(file),
                    contentType,
                    file.getSize()
            );

            return inTransaction(() -> UploadImageResponse.from(uploadImageRepository.saveAndFlush(image)));
        } catch (RuntimeException exception) {
            deleteAttemptedKeys(attemptedKeys);
            throw exception;
        }
    }

    static String thumbnailKey(String storageKey) {
        int dot = storageKey.lastIndexOf('.');

        return (dot < 0 ? storageKey : storageKey.substring(0, dot)) + THUMBNAIL_SUFFIX;
    }

    /**
     * 원본, 화면용 썸네일, DB 기록 순서로 지운다.
     *
     * <p>원본을 먼저 지우면 다음 단계가 실패해도 화면이 참조하는 썸네일은 남는다.
     * DB 기록은 마지막까지 보존하므로 실패한 삭제를 같은 이미지 ID로 다시 시도할 수 있다.</p>
     */
    @Transactional
    public void delete(Long imageId) {
        UploadImage image = uploadImageRepository.findById(imageId)
                .orElseThrow(() -> new BlogException(ErrorCode.IMAGE_NOT_FOUND));
        String originalKey = image.getStorageKey();
        String actualThumbnailKey = storageKeyFromUrl(image.getUrl());

        imageStorage.delete(originalKey);
        if (!originalKey.equals(actualThumbnailKey)) {
            imageStorage.delete(actualThumbnailKey);
        }
        uploadImageRepository.delete(image);
    }

    /**
     * DB 에 저장된 실제 URL에서 삭제할 객체 키를 꺼낸다.
     *
     * <p>현재 접미사로 다시 계산하지 않아야 과거 버전의 썸네일도 정확히 지울 수 있다.
     * URL 은 서버가 생성한 날짜/UUID 키만 사용하므로 경로가 곧 R2 객체 키다.</p>
     */
    private String storageKeyFromUrl(String imageUrl) {
        try {
            String path = URI.create(imageUrl).getPath();
            String storageKey = path != null && path.startsWith("/") ? path.substring(1) : path;

            if (storageKey == null || storageKey.isBlank()) {
                throw new IllegalArgumentException("이미지 URL에 저장소 키가 없습니다.");
            }

            return storageKey;
        } catch (IllegalArgumentException exception) {
            throw new BlogException(ErrorCode.IMAGE_DELETE_FAILED, "삭제할 이미지의 저장소 키를 확인할 수 없습니다.");
        }
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

    /**
     * 파일을 통째로 메모리에 읽는다.
     *
     * <p>스트림을 두 번 쓸 수 없어서다 — 저장소에 올리는 데 한 번, 썸네일을 만드는 데
     * 한 번 필요하다. 업로드 용량 상한이 걸려 있으므로 여기서 메모리가 터질 일은 없다.</p>
     */
    private byte[] read(MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return content.readAllBytes();
        } catch (IOException exception) {
            throw new BlogException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    private StoredImage store(String storageKey, String contentType, byte[] content) {
        try (InputStream stream = new ByteArrayInputStream(content)) {
            return imageStorage.store(storageKey, contentType, content.length, stream);
        } catch (IOException exception) {
            throw new BlogException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    private <T> T inTransaction(Supplier<T> action) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> action.get());
    }

    private void deleteAttemptedKeys(List<String> attemptedKeys) {
        for (int index = attemptedKeys.size() - 1; index >= 0; index--) {
            deleteQuietly(attemptedKeys.get(index));
        }
    }

    private void deleteQuietly(String storageKey) {
        try {
            imageStorage.delete(storageKey);
        } catch (RuntimeException cleanupException) {
            log.error("실패한 이미지 업로드를 정리하지 못했다. storageKey={}", storageKey, cleanupException);
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
