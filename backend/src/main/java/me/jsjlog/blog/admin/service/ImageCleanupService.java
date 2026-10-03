package me.jsjlog.blog.admin.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.admin.domain.UploadImage;
import me.jsjlog.blog.admin.dto.AdminImageCleanupResponse;
import me.jsjlog.blog.admin.dto.AdminImageListResponse;
import me.jsjlog.blog.admin.dto.AdminImageResponse;
import me.jsjlog.blog.admin.dto.AdminImageUsage;
import me.jsjlog.blog.admin.repository.ImageUsageRepository;
import me.jsjlog.blog.common.upload.ImageStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * 글에서 빠진 이미지를 정리한다.
 *
 * 글을 지우거나 고치면 그 글이 쓰던 이미지가 남는다. 지금도 업로드한 8장 중 5장이
 * 아무 글에도 안 쓰인 채 R2 에 있다.
 *
 * 삭제는 커밋 뒤에 한다. 트랜잭션 안에서 지우면 롤백됐을 때 글은 남고 파일만 없어진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageCleanupService {

    /** 마크다운 ![](url) 과 <img src="url"> 둘 다 찾는다. 붙여넣기로 들어간 태그가 섞여 있다 */
    private static final Pattern IMAGE_URL = Pattern.compile(
            "!\\[[^\\]]*\\]\\(([^)\\s]+)[^)]*\\)|<img[^>]+src=[\"']([^\"']+)[\"']");

    /**
     * 목록에서 뺄 기간.
     *
     * 사진만 올리고 아직 저장 안 한 글이 있으면 그 사진이 "안 쓰임" 으로 잡힌다.
     * 임시저장이 브라우저에만 있어서 서버는 그 글을 모른다.
     */
    private static final Duration GRACE_PERIOD = Duration.ofDays(7);

    private final ImageUsageRepository imageUsageRepository;
    private final ImageStorage imageStorage;

    /**
     * 올린 지 일주일 넘은 이미지 목록. 어디에 쓰이는지 같이 채워 준다.
     *
     * onlyUnused 가 true 면 아무 데서도 안 쓰는 것만 준다.
     */
    @Transactional(readOnly = true)
    public AdminImageListResponse findAll(boolean onlyUnused, Pageable pageable) {
        LocalDateTime cutoff = LocalDateTime.now().minus(GRACE_PERIOD);
        Page<UploadImage> page = onlyUnused
                ? imageUsageRepository.findUnusedOlderThan(cutoff, pageable)
                : imageUsageRepository.findOlderThan(cutoff, pageable);

        Map<String, List<AdminImageUsage>> usages = collectUsages(page.getContent());

        List<AdminImageResponse> items = page.getContent().stream()
                .map(image -> AdminImageResponse.of(
                        image, usages.getOrDefault(image.getUrl(), List.of())))
                .toList();

        long unusedBytes = items.stream()
                .filter(item -> item.usages().isEmpty())
                .mapToLong(AdminImageResponse::byteSize)
                .sum();

        return new AdminImageListResponse(
                items, unusedBytes,
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** 한 페이지 분량만 넘긴다. 본문 LIKE 가 그 범위에서만 돌게 하려는 것이다 */
    private Map<String, List<AdminImageUsage>> collectUsages(List<UploadImage> images) {
        if (images.isEmpty()) {
            return Map.of();
        }

        Set<String> urls = images.stream().map(UploadImage::getUrl).collect(Collectors.toSet());

        return Stream.of(
                        imageUsageRepository.findContentUsages(urls),
                        imageUsageRepository.findThumbnailUsages(urls),
                        imageUsageRepository.findHistoryUsages(urls),
                        imageUsageRepository.findHeroUsages(urls),
                        imageUsageRepository.findProfileUsages(urls))
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(AdminImageUsage::url));
    }

    /**
     * 화면에서 고른 것을 지운다.
     *
     * id 로 받아 URL 로 바꿔 deleteUnused 에 넘긴다. 목록을 띄워 둔 사이에 그 사진을
     * 새 글에 넣었을 수 있어서, 지우기 직전에 참조를 한 번 더 본다.
     */
    @Transactional
    public AdminImageCleanupResponse cleanup(List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return AdminImageCleanupResponse.empty();
        }

        Set<Long> requestedIds = imageIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (requestedIds.isEmpty()) {
            return AdminImageCleanupResponse.empty();
        }

        List<UploadImage> images = imageUsageRepository.findAllById(requestedIds);
        Set<String> urls = images.stream()
                .map(UploadImage::getUrl)
                .collect(Collectors.toSet());
        Set<Long> stillUsed = urls.isEmpty()
                ? Set.of()
                : imageUsageRepository.findStillUsed(urls).stream()
                        .map(UploadImage::getId)
                        .collect(Collectors.toSet());

        int deletedCount = 0;
        int skippedUsedCount = 0;
        int failedCount = 0;

        for (UploadImage image : images) {
            if (stillUsed.contains(image.getId())) {
                skippedUsedCount++;
                continue;
            }

            if (delete(image)) {
                deletedCount++;
            } else {
                failedCount++;
            }
        }

        return new AdminImageCleanupResponse(
                requestedIds.size(),
                deletedCount,
                skippedUsedCount,
                requestedIds.size() - images.size(),
                failedCount
        );
    }

    /** 글을 건드리기 전에 호출해야 한다. 저장한 뒤에는 원래 뭘 쓰고 있었는지 알 수 없다 */
    public Set<String> collectUsedUrls(String content, String thumbnailImageUrl) {
        Set<String> urls = new HashSet<>();

        if (StringUtils.hasText(content)) {
            Matcher matcher = IMAGE_URL.matcher(content);

            while (matcher.find()) {
                String url = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);

                if (StringUtils.hasText(url)) {
                    urls.add(url.trim());
                }
            }
        }

        if (StringUtils.hasText(thumbnailImageUrl)) {
            urls.add(thumbnailImageUrl.trim());
        }

        return urls;
    }

    /**
     * REQUIRES_NEW 를 여기 붙인다.
     *
     * AFTER_COMMIT 시점에는 원래 트랜잭션이 이미 끝나 있다. 그 상태에서 쓰면 커밋되지 않고
     * 예외도 없이 사라진다. 실제로 파일만 지워지고 upload_image 기록이 남았다.
     *
     * deleteUnused 쪽에 붙이면 같은 빈 안에서 호출하는 거라 프록시를 안 타서 소용없다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostImagesReleased(PostImagesReleasedEvent event) {
        deleteUnused(event.urls());
    }

    @Transactional
    public void deleteUnused(Set<String> candidates) {
        if (candidates.isEmpty()) {
            return;
        }

        List<UploadImage> known = imageUsageRepository.findByUrlIn(candidates);

        if (known.isEmpty()) {
            // 업로드 API 를 거치지 않은 외부 주소다
            return;
        }

        Set<Long> stillUsed = imageUsageRepository.findStillUsed(candidates).stream()
                .map(UploadImage::getId)
                .collect(Collectors.toSet());

        known.stream()
                .filter(image -> !stillUsed.contains(image.getId()))
                .forEach(this::delete);
    }

    /**
     * 저장소를 먼저 지우고 기록을 지운다. 순서를 바꾸면 삭제가 실패했을 때 파일을 찾을 수 없다.
     *
     * 실패해도 예외를 던지지 않고 false 를 반환한다. 글 저장은 이미 끝났으므로 자동 정리는
     * 나중에 다시 시도할 수 있어야 하고, 수동 정리는 이 값을 세어 관리자에게 실패 건수를 알린다.
     */
    private boolean delete(UploadImage image) {
        try {
            String originalKey = image.getStorageKey();
            String actualThumbnailKey = AdminImageService.storageKeyFromUrl(image.getUrl());

            imageStorage.delete(originalKey);
            if (!originalKey.equals(actualThumbnailKey)) {
                imageStorage.delete(actualThumbnailKey);
            }
            imageUsageRepository.delete(image);

            log.info("안 쓰는 이미지 삭제. key={}", image.getStorageKey());
            return true;
        } catch (RuntimeException exception) {
            log.warn("이미지 삭제 실패. 기록은 남긴다. key={}", image.getStorageKey(), exception);
            return false;
        }
    }

    public record PostImagesReleasedEvent(Set<String> urls) {
    }
}
