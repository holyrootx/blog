package me.jsjlog.blog.history.service;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.history.domain.CommentChange;
import me.jsjlog.blog.history.domain.CommentSnapshot;
import me.jsjlog.blog.history.domain.ContentHistory;
import me.jsjlog.blog.history.domain.ContentHistory.Action;
import me.jsjlog.blog.history.domain.ContentHistory.Target;
import me.jsjlog.blog.history.repository.ContentHistoryRepository;
import me.jsjlog.blog.post.domain.Comment;
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

    public void recordComment(Comment comment, Action action, CommentSnapshot before) {
        CommentSnapshot after = CommentSnapshot.from(comment);
        if (!Objects.equals(before, after)) {
            historyRepository.save(new ContentHistory(Target.COMMENT, comment.getId(), comment.getPost().getId(),
                    action, json(before), json(after)));
        }
    }

    /** 댓글 하나의 기록. 최신이 앞이다 */
    public List<CommentChange> commentChanges(Long commentId) {
        return historyRepository.findByTargetTypeAndTargetIdOrderByIdDesc(Target.COMMENT, commentId)
                .stream()
                .map(history -> new CommentChange(
                        history.getId(),
                        history.getAction(),
                        history.getCreatedAt(),
                        commentSnapshot(history.getBeforeSnapshot()),
                        commentSnapshot(history.getAfterSnapshot())
                ))
                .toList();
    }

    private String json(Object snapshot) {
        return snapshot == null ? null : objectMapper.writeValueAsString(snapshot);
    }

    private CommentSnapshot commentSnapshot(String json) {
        return json == null ? null : objectMapper.readValue(json, CommentSnapshot.class);
    }
}
