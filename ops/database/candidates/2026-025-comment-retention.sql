-- REVIEW CANDIDATE ONLY. 자동으로 실행하지 않는다.
-- 2026-025 댓글 보관·파기: comment 에 글쓴이가 지운 시각과 원문을 파기한 시각을 더한다.
-- MySQL 8. 운영 DB 에 적용하기 전 백업과 별도 DB 복원 리허설을 먼저 한다 (README 3절).
-- ddl-auto: update 인 동안에는 새 JAR 이 기동하며 같은 칸을 만든다.
-- INFORMATION_SCHEMA 로 먼저 확인하고, 이미 있으면 1번은 건너뛴다. DDL 은 암묵적으로 커밋된다.

-- 1. 칸 추가. 둘 다 비어 있을 수 있다
ALTER TABLE comment ADD COLUMN deleted_at DATETIME(6) NULL;
ALTER TABLE comment ADD COLUMN content_purged_at DATETIME(6) NULL;

-- 2. 지운 시각을 모르는 기존 삭제 댓글. 자동 파기 대상이 아니다
SELECT COUNT(*) AS deleted_without_timestamp
FROM comment
WHERE deleted = 1 AND deleted_at IS NULL;

-- 3. 근거가 있는 것만 채운다: 변경 기록에 남은 마지막 DELETE 시각.
--    그 뒤에 운영자가 되돌린(RESTORE) 기록이 있으면 글쓴이 삭제가 취소된 것이라 건너뛴다.
--    운영자가 가린(HIDE) 것은 건너뛰지 않는다. 앱도 가려진 뒤에 지운 시각을 그대로 둔다.
--    변경 기록은 기록된 날부터 6개월이 지나면 파기되므로, 근거가 사라지기 전에 실행해야 한다.
--    createdAt / updatedAt 으로 지운 시각을 짐작하지 않는다.
UPDATE comment c
JOIN (
    SELECT h.target_id, MAX(h.created_at) AS last_deleted_at
    FROM content_history h
    WHERE h.target_type = 'COMMENT' AND h.action = 'DELETE'
    GROUP BY h.target_id
) d ON d.target_id = c.id
SET c.deleted_at = d.last_deleted_at
WHERE c.deleted = 1
  AND c.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM content_history later
      WHERE later.target_type = 'COMMENT'
        AND later.target_id = c.id
        AND later.action = 'RESTORE'
        AND later.created_at > d.last_deleted_at
  );

-- 4. 다시 세어 근거가 없어 남은 수를 기록한다. 이 댓글들은 처리 방법을 따로 정한다
SELECT COUNT(*) AS still_without_timestamp
FROM comment
WHERE deleted = 1 AND deleted_at IS NULL AND hidden_by_admin = 0;
