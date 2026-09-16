package me.jsjlog.blog.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 이미지 업로드 설정.
 *
 * 저장 방식을 설정으로 나눈다. 로컬은 개발용이고 운영은 R2 다.
 *
 * 허용 형식을 여기서 읽는 이유는, 코드에 박아 두면 형식 하나 늘릴 때마다 배포해야 하기 때문이다.
 * 확장자가 아니라 실제 내용의 형식을 이 목록과 비교한다.
 */
@ConfigurationProperties(prefix = "blog.upload")
public record UploadProperties(
        Storage storage,
        String localDirectory,
        String publicPath,
        long maxSizeBytes,
        List<String> allowedContentTypes,
        R2 r2
) {

    public enum Storage {
        LOCAL, R2
    }

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
