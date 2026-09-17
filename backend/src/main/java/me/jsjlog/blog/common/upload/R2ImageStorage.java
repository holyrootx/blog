package me.jsjlog.blog.common.upload;

import java.io.InputStream;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import me.jsjlog.blog.common.config.UploadProperties;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;

/**
 * Cloudflare R2 저장소. S3 호환 API 를 쓴다.
 *
 * 공개 주소는 버킷 주소가 아니라 별도 도메인(publicBaseUrl)으로 만든다.
 * R2 의 S3 엔드포인트는 자격증명이 있어야 읽히므로 브라우저가 직접 받을 수 없다.
 */
@RequiredArgsConstructor
public class R2ImageStorage implements ImageStorage {

    /**
     * 올릴 때 못 넣으면 나중에 못 넣는다. 이미 올라간 객체에는 소급 적용되지 않아
     * 전부 다시 올려야 한다.
     *
     * 저장 키가 UUID 라 같은 주소의 내용이 바뀌는 일이 없어 immutable 이 안전하다.
     * 이게 없으면 Cloudflare 가 DYNAMIC 으로 보고 캐시하지 않아, 같은 그림을
     * 다시 열어도 매번 R2 까지 다녀온다. (실측: 2MB 이미지 재요청도 1.15초)
     */
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client s3Client;
    private final UploadProperties properties;

    @Override
    public StoredImage store(String storageKey, String contentType, long size, InputStream content) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.r2().bucket())
                .key(storageKey)
                .contentType(contentType)
                .contentLength(size)
                .cacheControl(CACHE_CONTROL)
                .build();

        try {
            // 길이를 함께 넘긴다. 스트림만 주면 SDK 가 전체를 메모리에 올려 길이를 센다
            s3Client.putObject(request, RequestBody.fromInputStream(content, size));
        } catch (S3Exception exception) {
            throw new BlogException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }

        return new StoredImage(storageKey, publicUrl(storageKey));
    }

    @Override
    public void delete(String storageKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.r2().bucket())
                .key(storageKey)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (S3Exception exception) {
            throw new BlogException(ErrorCode.IMAGE_DELETE_FAILED);
        }
    }

    private String publicUrl(String storageKey) {
        String base = properties.r2().publicBaseUrl();

        return base.endsWith("/") ? base + storageKey : base + "/" + storageKey;
    }
}
