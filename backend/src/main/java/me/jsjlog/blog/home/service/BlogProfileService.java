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

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class BlogProfileService {

    private final BlogProfileRepository blogProfileRepository;

    /**
     * id는 1로 고정 예정(관리자는 1명이기 때문)
     * @return
     */
    public BlogProfileResponse getBlogProfile(Long profileId) {

        Optional<BlogProfile> byId = blogProfileRepository.findById(profileId);
        if (byId.isEmpty()) {
            throw new BlogException(ErrorCode.PROFILE_NOT_FOUND);
        }

        BlogProfile blogProfile = byId.get();

        return BlogProfileResponse.from(blogProfile);
    }

    @Transactional
    public BlogProfileResponse updateBlogProfile(UpdateBlogProfileRequest updateBlogProfile) {
        Optional<BlogProfile> byId = blogProfileRepository.findById(updateBlogProfile.id());
        if (byId.isEmpty()) {
            throw new BlogException(ErrorCode.PROFILE_NOT_FOUND);
        }

        BlogProfile blogProfile = byId.get();
        blogProfile.update(blogProfile.getName(),
                blogProfile.getIntro(),
                blogProfile.getJob(),
                blogProfile.getAvatarImageUrl(),
                blogProfile.getGithubUrl(),
                blogProfile.getEmail()
                );

        return BlogProfileResponse.from(blogProfile);
    }
}
