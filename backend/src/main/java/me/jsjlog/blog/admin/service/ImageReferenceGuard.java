package me.jsjlog.blog.admin.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.domain.UploadImage;
import me.jsjlog.blog.admin.repository.ImageUsageRepository;
import me.jsjlog.blog.common.config.UploadProperties;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 이미지 참조 저장과 파일 삭제가 같은 업로드 기록을 잠근다. 잠금은 커밋까지 유지된다. */
@Component
@RequiredArgsConstructor
public class ImageReferenceGuard {
    private static final Pattern IMAGE_URL = Pattern.compile(
            "!\\[[^\\]]*\\]\\(([^)\\s]+)[^)]*\\)|<img[^>]+src=[\"']([^\"']+)[\"']");
    private static final Pattern UPLOAD_KEY = Pattern.compile(
            "\\d{4}/\\d{2}/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?:-thumbnail[^/]*)?\\.[a-zA-Z0-9]+(?:[?#].*)?");

    private final ImageUsageRepository images;
    private final UploadProperties properties;

    @Transactional(propagation = Propagation.MANDATORY)
    public void protect(String content, String imageUrl) {
        Set<String> urls = collectUrls(content, imageUrl);
        if (urls.isEmpty()) return;
        List<UploadImage> known = images.findByUrlIn(urls);
        Set<String> previouslyKnown = known.stream().map(UploadImage::getUrl).collect(Collectors.toSet());
        Set<String> locked = lockIds(known.stream().map(UploadImage::getId).toList()).stream()
                .map(UploadImage::getUrl).collect(Collectors.toSet());
        for (String url : urls) {
            if (!locked.contains(url) && (previouslyKnown.contains(url) || isManagedUpload(url))) {
                throw new BlogException(ErrorCode.IMAGE_REFERENCE_UNAVAILABLE);
            }
        }
    }

    /** 기본키로 하나씩 잠근다. URL 검색에 잠금을 걸어 업로드 대장 전체를 잠그지 않는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UploadImage> lockIds(Collection<Long> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().sorted()
                .map(images::findLockedById).flatMap(java.util.Optional::stream).toList();
    }

    private boolean isManagedUpload(String url) {
        if (properties.r2() == null || !StringUtils.hasText(properties.r2().publicBaseUrl())) return false;
        String base = properties.r2().publicBaseUrl().replaceAll("/+$", "") + "/";
        return url.startsWith(base) && UPLOAD_KEY.matcher(url.substring(base.length())).matches();
    }

    public static Set<String> collectUrls(String content, String imageUrl) {
        Set<String> urls = new HashSet<>();
        if (StringUtils.hasText(content)) {
            var matcher = IMAGE_URL.matcher(content);
            while (matcher.find()) {
                String url = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                if (StringUtils.hasText(url)) urls.add(url.trim());
            }
        }
        if (StringUtils.hasText(imageUrl)) urls.add(imageUrl.trim());
        return urls;
    }
}
