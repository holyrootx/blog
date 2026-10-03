-- REVIEW CANDIDATE ONLY. Copy of the original 2026-020 manual SQL; not auto-applied.
-- MySQL 8.4. Verify the actual database baseline before considering this change.
-- Check INFORMATION_SCHEMA first. Do not run if these objects already exist.
-- DDL commits implicitly; take a database backup before applying.

ALTER TABLE post ADD COLUMN deleted_at DATETIME(6) NULL;

CREATE TABLE content_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_type ENUM('COMMENT', 'POST') NOT NULL,
    target_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    action ENUM('CREATE', 'DELETE', 'HIDE', 'PUBLISH', 'RESTORE', 'SCHEDULE', 'UNPUBLISH', 'UPDATE') NOT NULL,
    snapshot_version INT NOT NULL,
    before_snapshot LONGTEXT NULL,
    after_snapshot LONGTEXT NOT NULL,
    created_by VARCHAR(255) NULL,
    updated_by VARCHAR(255) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_content_history_target (target_type, target_id, id),
    INDEX idx_content_history_post (post_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- No rows are removed and no existing statuses are changed.
-- No foreign keys: audit snapshots survive future permanent deletion.
