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

    public enum Target { POST, COMMENT }
    public enum Action { CREATE, UPDATE, DELETE, RESTORE, PUBLISH, SCHEDULE, UNPUBLISH, HIDE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20, updatable = false)
    private Target targetType;

    // History survives any future permanent deletion of the original entity.
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
