package me.jsjlog.blog.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 이미지 업로드 설정.
 *
 * 파일은 R2 에만 둔다. 저장 방식을 고르는 설정은 없다 — 개발과 운영이 갈리면
 * 실제로 배포되는 경로가 가장 덜 테스트된다.
 *
 * 허용 형식을 설정에서 읽는 이유는, 코드에 박아 두면 형식 하나 늘릴 때마다 배포해야 하기 때문이다.
 * 확장자가 아니라 실제 내용의 형식을 이 목록과 비교한다.
 */
@ConfigurationProperties(prefix = "blog.upload")
public record UploadProperties(
        long maxSizeBytes,
        List<String> allowedContentTypes,
        R2 r2
) {

    public record R2(
            String endpoint,
            String bucket,
            String region,
            String accessKeyId,
            String secretAccessKey,
            String publicBaseUrl
    ) {
    }

    public boolean allows(String contentType) {
        return contentType != null && allowedContentTypes.contains(contentType.toLowerCase());
    }
}
