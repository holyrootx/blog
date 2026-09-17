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

    private final S3Client s3Client;
    private final UploadProperties properties;

    @Override
    public StoredImage store(String storageKey, String contentType, long size, InputStream content) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.r2().bucket())
                .key(storageKey)
                .contentType(contentType)
                .contentLength(size)
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
