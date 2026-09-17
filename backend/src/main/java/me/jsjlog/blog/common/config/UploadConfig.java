package me.jsjlog.blog.common.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import me.jsjlog.blog.common.upload.ImageStorage;
import me.jsjlog.blog.common.upload.R2ImageStorage;

/**
 * 업로드 저장소 구성.
 *
 * 설정이 비어 있으면 애플리케이션이 뜨지 않는다. 대안 저장소를 두면 설정을 빠뜨렸을 때
 * 조용히 다른 곳에 쓰게 되는데, 그 파일들은 다음 배포에 사라진다.
 */
@Configuration
@EnableConfigurationProperties(UploadProperties.class)
public class UploadConfig {

    @Bean
    public S3Client r2Client(UploadProperties properties) {
        UploadProperties.R2 r2 = properties.r2();
        requireConfigured(r2);

        return S3Client.builder()
                .endpointOverride(URI.create(r2.endpoint()))
                .region(Region.of(r2.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(r2.accessKeyId(), r2.secretAccessKey())))
                // R2 는 가상 호스트 방식 주소를 쓰지 않는다. 버킷을 경로에 넣어야 한다
                .forcePathStyle(true)
                .build();
    }

    @Bean
    public ImageStorage r2ImageStorage(S3Client r2Client, UploadProperties properties) {
        return new R2ImageStorage(r2Client, properties);
    }

    /**
     * 설정이 비었을 때 무슨 값이 없는지 알려준다.
     *
     * 그냥 두면 SDK 가 "The URI scheme of endpointOverride must not be null" 로 죽는데,
     * 그 문구만 보고 R2 환경변수를 빠뜨렸다는 걸 알아내기 어렵다.
     */
    private void requireConfigured(UploadProperties.R2 r2) {
        List<String> missing = new ArrayList<>();

        if (!StringUtils.hasText(r2.endpoint())) {
            missing.add("R2_ENDPOINT");
        }
        if (!StringUtils.hasText(r2.bucket())) {
            missing.add("R2_BUCKET");
        }
        if (!StringUtils.hasText(r2.accessKeyId())) {
            missing.add("R2_ACCESS_KEY_ID");
        }
        if (!StringUtils.hasText(r2.secretAccessKey())) {
            missing.add("R2_SECRET_ACCESS_KEY");
        }
        if (!StringUtils.hasText(r2.publicBaseUrl())) {
            missing.add("R2_PUBLIC_BASE_URL");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "R2 업로드 설정이 비어 있습니다. 다음 값을 확인해 주세요: " + String.join(", ", missing));
        }
    }
}
