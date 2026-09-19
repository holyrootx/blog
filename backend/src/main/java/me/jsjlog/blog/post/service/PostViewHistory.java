package me.jsjlog.blog.post.service;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/** 한 세션에서 이미 조회수에 반영한 글을 기억한다. */
final class PostViewHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    static final String SESSION_ATTRIBUTE_NAME = PostViewHistory.class.getName();

    private final Set<Long> viewedPostIds = new HashSet<>();

    boolean hasViewed(Long postId) {
        return viewedPostIds.contains(postId);
    }

    void record(Long postId) {
        viewedPostIds.add(postId);
    }
}
