package me.jsjlog.blog.home.service;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.home.domain.BlogProfile;
import me.jsjlog.blog.home.dto.BlogProfileResponse;
import me.jsjlog.blog.home.dto.UpdateBlogProfileRequest;
import me.jsjlog.blog.home.repository.BlogProfileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
@Slf4j
@RequiredArgsConstructor
public class BlogProfileService {

    private static final long DEFAULT_PROFILE_ID = 1L;
    private static final int NAME_MAX_LENGTH = 100;
    private static final int INTRO_MAX_LENGTH = 500;
    private static final int JOB_MAX_LENGTH = 100;
    private static final int URL_MAX_LENGTH = 500;
    private static final int EMAIL_MAX_LENGTH = 255;

    private final BlogProfileRepository blogProfileRepository;

    /**
     * id는 1로 고정 예정(관리자는 1명이기 때문)
     * @return
     */
    public BlogProfileResponse getBlogProfile(Long profileId) {
        return BlogProfileResponse.from(findProfile(profileId));
    }

    @Transactional
    public BlogProfileResponse updateBlogProfile(UpdateBlogProfileRequest request) {
        validate(request);

        BlogProfile blogProfile = findProfile(request.id());
        blogProfile.update(
                request.name().trim(),
                request.intro().trim(),
                request.job().trim(),
                blankToNull(request.avatarImageUrl()),
                blankToNull(request.githubUrl()),
                blankToNull(request.email()),
                request.blogStartedAt()
        );

        return BlogProfileResponse.from(blogProfile);
    }

    public Long getDaysSinceStart() {
        return blogProfileRepository.findById(DEFAULT_PROFILE_ID)
                .map(profile -> profile.daysSinceStart(LocalDate.now()))
                .orElse(null);
    }

    private BlogProfile findProfile(Long profileId) {
        if (profileId == null) {
            throw new BlogException(ErrorCode.PROFILE_NOT_FOUND);
        }

        return blogProfileRepository.findById(profileId)
                .orElseThrow(() -> new BlogException(ErrorCode.PROFILE_NOT_FOUND));
    }

    private void validate(UpdateBlogProfileRequest request) {
        if (request == null
                || !StringUtils.hasText(request.name())
                || !StringUtils.hasText(request.intro())
                || !StringUtils.hasText(request.job())) {
            throw new BlogException(ErrorCode.PROFILE_REQUIRED_VALUE_MISSING);
        }

        if (request.name().trim().length() > NAME_MAX_LENGTH
                || request.intro().trim().length() > INTRO_MAX_LENGTH
                || request.job().trim().length() > JOB_MAX_LENGTH
                || length(request.avatarImageUrl()) > URL_MAX_LENGTH
                || length(request.githubUrl()) > URL_MAX_LENGTH
                || length(request.email()) > EMAIL_MAX_LENGTH) {
            throw new BlogException(ErrorCode.PROFILE_VALUE_TOO_LONG);
        }

        if (request.blogStartedAt() != null && request.blogStartedAt().isAfter(LocalDate.now())) {
            throw new BlogException(ErrorCode.BLOG_STARTED_AT_FUTURE);
        }
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
