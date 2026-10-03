package me.jsjlog.blog.history.service;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.history.domain.CommentSnapshot;
import me.jsjlog.blog.history.domain.ContentHistory;
import me.jsjlog.blog.history.domain.ContentHistory.Action;
import me.jsjlog.blog.history.domain.ContentHistory.Target;
import me.jsjlog.blog.history.domain.PostSnapshot;
import me.jsjlog.blog.history.repository.ContentHistoryRepository;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ContentHistoryService {

    private final ContentHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    public void recordPost(Post post, Action action, PostSnapshot before) {
        PostSnapshot after = PostSnapshot.from(post);
        if (!Objects.equals(before, after)) {
            historyRepository.save(new ContentHistory(Target.POST, post.getId(), post.getId(), action,
                    json(before), json(after)));
        }
    }

    public void recordComment(Comment comment, Action action, CommentSnapshot before) {
        CommentSnapshot after = CommentSnapshot.from(comment);
        if (!Objects.equals(before, after)) {
            historyRepository.save(new ContentHistory(Target.COMMENT, comment.getId(), comment.getPost().getId(),
                    action, json(before), json(after)));
        }
    }

    private String json(Object snapshot) {
        return snapshot == null ? null : objectMapper.writeValueAsString(snapshot);
    }
}
