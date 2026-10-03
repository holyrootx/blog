package me.jsjlog.blog.post.service;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

/** 직접 발행, 예약 수정, 자동 발행이 공유하는 공개 조건. */
public final class PostPublicationPolicy {

    private PostPublicationPolicy() {
    }

    public static void checkPublishable(String content, String excerpt) {
        if (!StringUtils.hasText(content)) {
            throw new BlogException(ErrorCode.POST_CONTENT_REQUIRED);
        }
        if (!StringUtils.hasText(excerpt)) {
            throw new BlogException(ErrorCode.POST_EXCERPT_REQUIRED);
        }
    }
}
