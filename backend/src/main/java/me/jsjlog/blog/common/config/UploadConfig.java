package me.jsjlog.blog.common.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import me.jsjlog.blog.common.upload.ImageStorage;
import me.jsjlog.blog.common.upload.LocalImageStorage;
import me.jsjlog.blog.common.upload.R2ImageStorage;

/**
 * 업로드 저장소 구성.
 *
 * 설정값 하나로 갈아끼운다. 두 구현이 동시에 뜨지 않도록 조건을 서로 배타적으로 둔다.
 */
@Configuration
@EnableConfigurationProperties(UploadProperties.class)
public class UploadConfig {

    @Bean
    @ConditionalOnProperty(name = "blog.upload.storage", havingValue = "r2")
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
    @ConditionalOnProperty(name = "blog.upload.storage", havingValue = "r2")
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

    @Bean
    @ConditionalOnProperty(name = "blog.upload.storage", havingValue = "local", matchIfMissing = true)
    public ImageStorage localImageStorage(UploadProperties properties) {
        return new LocalImageStorage(properties);
    }

    /**
     * 로컬에 저장한 이미지를 브라우저가 받아갈 수 있게 연다.
     *
     * 이 경로는 {@code /api/**} 밖이라 보안 필터를 타지 않는다. 이미지는 공개 글에 실리므로
     * 로그인 없이 열려 있어야 맞다.
     */
    @Bean
    @ConditionalOnProperty(name = "blog.upload.storage", havingValue = "local", matchIfMissing = true)
    public WebMvcConfigurer localUploadResourceConfigurer(UploadProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                Path root = Path.of(properties.localDirectory()).toAbsolutePath().normalize();

                registry.addResourceHandler(properties.publicPath() + "/**")
                        .addResourceLocations(root.toUri().toString());
            }
        };
    }
}
