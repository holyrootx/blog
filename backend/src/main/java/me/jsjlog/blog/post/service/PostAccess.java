package me.jsjlog.blog.post.service;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostAccess {

    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public Post findActive(Long postId) {
        return requireActive(postRepository.findById(postId)
                .orElseThrow(() -> new BlogException(ErrorCode.POST_NOT_FOUND)));
    }

    // All mutations lock the parent post first, including comment mutations.
    @Transactional(propagation = Propagation.MANDATORY)
    public Post lockActive(Long postId) {
        return requireActive(postRepository.findLockedById(postId)
                .orElseThrow(() -> new BlogException(ErrorCode.POST_NOT_FOUND)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Post lockPublished(Long postId) {
        Post post = lockActive(postId);
        if (!post.isPublished()) {
            throw new BlogException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private Post requireActive(Post post) {
        if (post.isDeleted()) {
            throw new BlogException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }
}
