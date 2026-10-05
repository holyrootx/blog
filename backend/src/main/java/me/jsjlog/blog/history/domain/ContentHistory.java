package me.jsjlog.blog.history.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.jsjlog.blog.common.domain.BaseEntity;

@Getter
@Entity
@Table(name = "content_history", indexes = {
        @Index(name = "idx_content_history_target", columnList = "target_type,target_id,id"),
        @Index(name = "idx_content_history_post", columnList = "post_id,id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentHistory extends BaseEntity {

    // 글 기록은 더 남기지 않는다. 이전에 쌓인 행을 읽을 수 있게 값은 둔다. 6개월 파기로 사라진다
    public enum Target { POST, COMMENT }
    public enum Action { CREATE, UPDATE, DELETE, RESTORE, PUBLISH, SCHEDULE, UNPUBLISH, HIDE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20, updatable = false)
    private Target targetType;

    // 댓글 원문 파기 시 해당 댓글 기록도 지운다. 예전 글 사본은 글 영구 삭제 시 함께 지운다.
    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20, updatable = false)
    private Action action;

    @Column(name = "snapshot_version", nullable = false, updatable = false)
    private int snapshotVersion;

    @Column(name = "before_snapshot", columnDefinition = "LONGTEXT", updatable = false)
    private String beforeSnapshot;

    @Column(name = "after_snapshot", nullable = false, columnDefinition = "LONGTEXT", updatable = false)
    private String afterSnapshot;

    public ContentHistory(Target targetType, Long targetId, Long postId, Action action,
                          String beforeSnapshot, String afterSnapshot) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.postId = postId;
        this.action = action;
        this.snapshotVersion = 1;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
    }
}
