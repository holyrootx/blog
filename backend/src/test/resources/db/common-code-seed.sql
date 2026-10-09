-- 공통 코드 초기값. 이미 있으면 넣지 않는다 — 여러 번 실행해도 되고, 관리 화면에서 고친 이름을 덮어쓰지 않는다.
-- 서버 코드(enum)와 맞아야 서버가 뜬다(CommonCodes). enum 에 값을 더하면 여기에도 더한다.
-- 테스트는 이 파일을 자동으로 실행한다. 운영·로컬 DB 에는 배포 전에 한 번 실행한다.

INSERT INTO common_code_group (group_code, group_name, description, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'MEMBER_STATUS', '회원 상태', '회원 한 명의 지금 상태', TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code_group WHERE group_code = 'MEMBER_STATUS');

INSERT INTO common_code_group (group_code, group_name, description, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'MEMBER_STATUS_REASON', '회원 상태 변경 사유', '회원 상태 이력에 남기는 까닭', TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code_group WHERE group_code = 'MEMBER_STATUS_REASON');

INSERT INTO common_code_group (group_code, group_name, description, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'ACTOR_TYPE', '처리자', '변경을 한 쪽', TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code_group WHERE group_code = 'ACTOR_TYPE');

INSERT INTO common_code (group_code, code, code_name, description, sort_order, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'MEMBER_STATUS', 'ACTIVE', '활동', NULL, 1, TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code WHERE group_code = 'MEMBER_STATUS' AND code = 'ACTIVE');

INSERT INTO common_code (group_code, code, code_name, description, sort_order, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'MEMBER_STATUS', 'SUSPENDED', '정지', '로그인할 수 없다', 2, TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code WHERE group_code = 'MEMBER_STATUS' AND code = 'SUSPENDED');

INSERT INTO common_code (group_code, code, code_name, description, sort_order, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'MEMBER_STATUS', 'WITHDRAWN', '탈퇴', '30일 안에는 같은 계정으로 복원할 수 있다', 3, TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code WHERE group_code = 'MEMBER_STATUS' AND code = 'WITHDRAWN');

INSERT INTO common_code (group_code, code, code_name, description, sort_order, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'MEMBER_STATUS_REASON', 'SIGNUP', '가입', NULL, 1, TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code WHERE group_code = 'MEMBER_STATUS_REASON' AND code = 'SIGNUP');

INSERT INTO common_code (group_code, code, code_name, description, sort_order, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'MEMBER_STATUS_REASON', 'WITHDRAW', '탈퇴', NULL, 2, TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code WHERE group_code = 'MEMBER_STATUS_REASON' AND code = 'WITHDRAW');

INSERT INTO common_code (group_code, code, code_name, description, sort_order, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'MEMBER_STATUS_REASON', 'REACTIVATE', '복원', '탈퇴 후 기간 안에 예전 계정을 되살렸다', 3, TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code WHERE group_code = 'MEMBER_STATUS_REASON' AND code = 'REACTIVATE');

INSERT INTO common_code (group_code, code, code_name, description, sort_order, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'MEMBER_STATUS_REASON', 'REJOIN', '새로 가입', '복원 대신 새 계정을 만들었다', 4, TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code WHERE group_code = 'MEMBER_STATUS_REASON' AND code = 'REJOIN');

INSERT INTO common_code (group_code, code, code_name, description, sort_order, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'MEMBER_STATUS_REASON', 'SUSPEND', '정지', NULL, 5, TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code WHERE group_code = 'MEMBER_STATUS_REASON' AND code = 'SUSPEND');

INSERT INTO common_code (group_code, code, code_name, description, sort_order, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'MEMBER_STATUS_REASON', 'UNSUSPEND', '정지 해제', NULL, 6, TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code WHERE group_code = 'MEMBER_STATUS_REASON' AND code = 'UNSUSPEND');

INSERT INTO common_code (group_code, code, code_name, description, sort_order, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'MEMBER_STATUS_REASON', 'RESTORE_EXPIRED', '복원 기한 만료', '복원 기간이 지나 복원 정보를 파기했다', 7, TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code WHERE group_code = 'MEMBER_STATUS_REASON' AND code = 'RESTORE_EXPIRED');

INSERT INTO common_code (group_code, code, code_name, description, sort_order, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'ACTOR_TYPE', 'SELF', '본인', NULL, 1, TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code WHERE group_code = 'ACTOR_TYPE' AND code = 'SELF');

INSERT INTO common_code (group_code, code, code_name, description, sort_order, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'ACTOR_TYPE', 'ADMIN', '관리자', NULL, 2, TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code WHERE group_code = 'ACTOR_TYPE' AND code = 'ADMIN');

INSERT INTO common_code (group_code, code, code_name, description, sort_order, is_enabled, created_at, created_by, updated_at, updated_by)
SELECT 'ACTOR_TYPE', 'SYSTEM', '시스템', '정해진 일정으로 서버가 처리', 3, TRUE, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM common_code WHERE group_code = 'ACTOR_TYPE' AND code = 'SYSTEM');
