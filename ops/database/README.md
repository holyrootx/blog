# MySQL 운영과 오픈 전 validate 전환

현재 공통 `application.yaml`은 `ddl-auto: update`이며 `prod`도 상속합니다. **2026-10-04 운영 프로세스에서도 `prod` 활성화와 DDL 설정 재정의가 없는 것을 확인했습니다.** 확인 범위와 런타임 버전은 [운영 환경 기록](../runtime-2026-10-04.md)에 있습니다. 이 설정 확인은 스키마 전체 대조나 백업 복원 검증의 완료를 뜻하지 않습니다.

현재 개발 단계에서는 `update`를 유지합니다. 블로그 오픈 전에 **스키마 확정 → DB 초기화 → DDL만으로 테이블 생성 → 데이터 재삽입 → 검증 → `validate` 전환**을 별도 작업으로 진행합니다. Flyway 도입은 이 전환의 필수 조건이 아니며, 필요하면 복원 검증 이후 별도 변경으로 결정합니다.

검토용 SQL은 `ops/database/candidates`에 두며 애플리케이션 classpath와 배포 스크립트에서 자동 실행하지 않습니다. 이 문서 변경은 운영 DB나 애플리케이션 설정을 변경하지 않습니다.

## 현재 준비된 변경

- `inspection/schema-state.sql`: 버전·SQL mode·테이블·컬럼·인덱스·FK·제약조건을 읽는 SELECT만 있다.
- `scripts/inspect-database.sh`: 기존 MySQL login-path를 사용해 위 메타데이터와 데이터 없는 schema dump, 존재하는 경우 Flyway history를 **저장소 밖의 새 디렉터리**에 저장한다. 운영 서버 접속이나 계정 생성은 자동으로 하지 않는다.
- `candidates/2026-020-post-trash-history.sql`: 기존 로컬 수동 SQL의 검토용 사본이다. `post.deleted_at`과 `content_history`가 없다고 확인된 스키마에만 필요하다. 기존 원본 SQL은 변경하지 않는다.
- 예약 취소는 기존 `UNPUBLISH` 이력과 전후 상태 스냅샷으로 구분한다. 이 수정만을 위한 새 ENUM 값이나 DDL은 필요하지 않다.

## 1. 로컬 Mac에서 준비

1. 검토 대상 SHA, 직전 배포 JAR, 실제 적용 중인 SHA를 각각 기록한다. GitHub 성공 실행은 서버 실행 SHA와 같은 증거가 아니다.
2. 신뢰할 수 있는 호스트 키를 확인하고 기존 관리 경로로 서버에 접속한다. 키/DB 암호를 문서·터미널 출력·GitHub 로그에 붙여넣지 않는다.
3. `inspection` SQL/스크립트와 SHA, 새/이전 JAR를 검증할 환경으로 전달한다. 백업·증거 폴더는 저장소 밖, 권한 700/600으로 관리한다.

## 2. 운영 서버에서 읽기 전용 조사

서버의 실제 `.env`와 systemd unit을 **값을 출력하지 않는 방식으로** 검토한다. 확인할 것은 prod profile, 대상 DB 식별, `SPRING_JPA_HIBERNATE_DDL_AUTO` 또는 CLI override 유무, 현재 실행 JAR 경로다. `cat .env`, `env`, 무차별 `systemctl show Environment`를 공유 로그에 실행하지 않는다.

이미 안전하게 등록된 읽기 전용 MySQL login-path가 있다고 가정한 조사 명령:

```bash
bash scripts/inspect-database.sh blog-inspect blog /private-secure-path/schema-evidence-20261003
```

`blog-inspect`와 출력 경로는 환경에서 확인한 값으로 바꾼다. login-path가 없으면 `mysql_config_editor`의 비밀번호 프롬프트로 설정하고, 필요한 SELECT/SHOW VIEW 범위 계정을 DBA가 지정한다. 스크립트는 행 데이터·비밀번호·계정 원문을 조회하지 않는다. 덤프의 view 정의 등에 운영 계정 식별자가 포함될 수 있으므로 schema evidence도 공개하지 않는다. 테이블 통계의 `approximate_rows`는 정확한 행 수 검증에 사용하지 않는다.

MySQL 프롬프트에서 추가로 확인할 핵심 메타데이터:

```sql
SELECT VERSION(), DATABASE();
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='post' AND COLUMN_NAME='deleted_at';
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='content_history';
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('flyway_schema_history', 'databasechangelog');
```

기준점은 실제 이 출력과 엔티티/기존 DDL을 대조한 후 정한다. `content_history`가 이미 존재하거나 일부만 적용되었다면 2026-020을 다시 실행하지 않는다. 이력 테이블이 있으면 컬럼/enum/인덱스 정의 전체를 비교한다.

## 3. 백업과 별도 MySQL 복원 검증

운영 백업 수단·최신 복원 성공 기록을 먼저 확인한다. 검증된 기존 백업이 없다면 합의한 점검 창에서 백업을 준비한다. 아래는 **InnoDB와 해당 권한을 확인한 뒤** 사용할 예시다. 백업 중 DDL을 실행하지 않는다. `--single-transaction`은 모든 스토리지 엔진의 일관성이나 백업 중 DDL 안전성을 보장하지 않는다. [MySQL mysqldump 문서](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html)

서버 셸:

```bash
umask 077
mysqldump --login-path=blog-backup --single-transaction --quick \
  --routines --events --triggers --no-tablespaces --set-gtid-purged=OFF \
  blog > /private-secure-path/blog-before-upgrade.sql
sha256sum /private-secure-path/blog-before-upgrade.sql
```

**운영과 분리한 MySQL 인스턴스**에서 새 DB를 만든다. 엔진 버전·SQL mode·문자셋/콜레이션은 조사 결과와 맞춘다. 아래 예시 콜레이션을 무조건 운영값으로 간주하지 않는다.

```sql
CREATE DATABASE blog_upgrade_check CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

검증 환경 셸:

```bash
mysql --login-path=blog-clone blog_upgrade_check < /private-secure-path/blog-before-upgrade.sql
bash scripts/inspect-database.sh blog-clone blog_upgrade_check /private-secure-path/clone-before
```

복원 직후 정확한 주요 테이블 COUNT, PK 범위, 상태별 개수, FK 무결성, 샘플 본문/댓글/이력의 hash를 원본 백업 기준과 대조한다. 비교 결과만 기록하고 회원 데이터·본문 원문을 공개 로그에 남기지 않는다. 스케줄러가 데이터를 변경할 수 있으므로 검증 앱은 운영 네트워크/R2/OAuth 자격증명과 분리하고 첫 비교가 끝나기 전 실행하지 않는다.

복원 스키마가 후보 SQL 전제와 일치하는 경우에만 필요한 파일을 순서대로 적용한다. 이는 복원 DB용 명령이며 운영 DB로 대상을 바꾸어 실행하는 승인은 별도다.

```bash
# 2026-020 objects가 이미 있으면 이 명령은 생략한다.
mysql --login-path=blog-clone blog_upgrade_check < ops/database/candidates/2026-020-post-trash-history.sql
bash scripts/inspect-database.sh blog-clone blog_upgrade_check /private-secure-path/clone-after
```

새 JAR를 이 복원 DB에 연결하고 `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` override로 기동 검증한다. 빈 스키마를 새로 만드는 테스트와 구버전 데이터 업그레이드 테스트를 각각 수행한다. 이 단계에 필요한 DB URL/비밀번호는 검증 환경의 비밀 설정으로 제공한다. R2/소셜 인증/외부 발송은 테스트 대체값을 사용한다.

검증할 동작: 공개/비공개/삭제/복구 글 조회, 댓글·답글·알림 공개 조건, 이력 조회, 예약 후 수정·취소, 실패 rollback 시 데이터 보존. 예약 취소를 한 번 실행해 `UNPUBLISH` 이력의 SCHEDULED → DRAFT 전후 상태가 보존되는지도 확인한다. H2 테스트 성공만으로 MySQL ENUM, FK, 인덱스, DDL 호환성을 통과 처리하지 않는다.

이후 **직전 배포 JAR도 동일 업그레이드 DB에 연결**해 조회·이력 역직렬화·시작 시 DDL 동작을 확인한다. 이전 코드의 엔티티/enum과 `ddl-auto:update`가 새 데이터·스키마를 호환하는지 함께 본다. 실패하면 기존 JAR를 자동 복구 대상에 두지 말고 호환 가능한 이전 산출물을 먼저 준비한다. 자동 파일 복구는 스키마나 신규 행을 삭제하지 않는다.

## 4. 블로그 오픈 전 DB 재구성과 validate 전환

이 절은 앞으로 수행할 작업입니다. 현재 서버의 `update`를 바로 바꾸거나 운영 데이터를 초기화하는 실행 명령은 아닙니다.

1. **스키마 확정:** 실제 MySQL 스키마, 현재 엔티티, 필요한 제약조건·인덱스를 대조하고 검토한 DDL 파일을 확정합니다. 남길 데이터와 다시 넣을 초기 데이터를 구분하고, FK 의존 순서에 맞는 재삽입 절차를 준비합니다.
2. **복원 리허설:** 위 백업·복원 절차를 별도 MySQL 인스턴스에서 통과시킵니다. 빈 DB에 확정 DDL만으로 테이블을 만들고 데이터를 재삽입합니다. Hibernate `update`로 빠진 테이블이나 컬럼을 보완하지 않습니다.
3. **검증 환경 기동:** `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`로 실행해 엔티티와 테이블의 호환성을 확인합니다. 행 수·PK·FK·인덱스·데이터 무결성은 별도로 비교하고, 글·댓글·회원·이력·업로드 등 주요 기능을 확인합니다.
4. **오픈 전 실행 계획 확정:** 백업 위치·복구 지점, 유지할 데이터, 쓰기 중지 시간, 사용할 DDL·데이터 파일의 checksum, 작업 대상 DB를 확인합니다. DB 초기화는 이 계획에 따라 별도 작업으로 실행합니다.
5. **DB 재구성:** 쓰기를 중지하고 최종 백업을 확보한 뒤, 확정 대상 DB를 초기화합니다. 검토된 DDL로 테이블을 생성하고 준비한 데이터를 의존 순서에 따라 재삽입합니다.
6. **최종 검증과 설정 전환:** 데이터·스키마·업무 기능 검증을 마치고 운영 설정을 `validate`로 전환합니다. 재시작 후 상태 API와 DB 조회를 함께 검사하고, 실행 SHA·DDL·데이터 파일 checksum·유효 설정·검증 결과를 기록합니다.

MySQL DDL은 일반 트랜잭션 rollback만으로 되돌릴 수 없습니다. 실패 시 부분 적용 여부를 확인하고 리허설에서 검증한 복원 절차를 따릅니다. 애플리케이션 JAR만 이전 버전으로 바꾸는 작업은 DB 복원을 대신하지 않습니다. [MySQL implicit commit 문서](https://dev.mysql.com/doc/refman/8.0/en/implicit-commit.html)

Flyway를 도입한다면 이 작업과 분리해 SQL 이력 관리, 빈 DB 생성, 기존 DB 기준점, 재실행 검증을 설계합니다. 현재 저장소에 Flyway를 추가하거나 자동 migration을 활성화하지 않습니다.

## 검증 기록 체크리스트

DB 변경·복원 검증과 오픈 전 전환의 결과는 다음 증거로 기록합니다. 배포에서 사용하는 `BLOG_SCHEMA_REVISION=2026-020` 값만으로 아래 검증을 모두 완료했다고 판단하지 않습니다.

| 증거 | 필요한 기록 |
| --- | --- |
| 실제 운영 기준점 | 실행 SHA, MySQL 버전, schema hash, ddl-auto 실제 유효값, 기존 이력 |
| 백업 복원 | 백업 hash/시각, 별도 DB 복원 성공, 주요 데이터/무결성 비교 |
| 업그레이드 | 적용 SQL checksum/순서, 변경 전후 schema, 새 JAR validate/업무 회귀 결과 |
| 복구 호환성 | 새 글·댓글·예약 취소 이력 행을 포함한 DB에서 복구 대상 JAR 조회·기동 성공 |
| 운영 적용 | 대상 재확인, 실제 적용 이력/결과, HTTP/DB smoke, 복구 위치 |

2026-10-04 README 보완에서는 운영 런타임 버전과 DDL 설정만 읽기 전용으로 확인했습니다. 이 작업에서 DB 초기화·재삽입·복원·Flyway 적용·`validate` 전환은 실행하지 않았습니다. 기존 복원·배포 검증 결과는 해당 작업 기록과 대조하고, 이 문서의 절차를 검증 완료 기록으로 대신하지 않습니다.
