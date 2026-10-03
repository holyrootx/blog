# MySQL 이력 관리와 validate 전환안

현재 소스의 `application.yaml`은 `ddl-auto:update`를 공통 설정으로 두며 prod도 상속한다. 서버 환경변수 override, 운영 스키마 및 적용 이력은 아직 확인하지 않았다. 이 변경은 Flyway를 런타임에 추가하거나 현재 ddl-auto를 변경하지 않는다. 검증되지 않은 기준점으로 운영에 자동 DDL을 실행하지 않기 위해 후보 SQL은 `ops/database/candidates`에 두고 애플리케이션 classpath/배포 스크립트에서는 읽지 않는다.

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

## 4. Flyway 기준점 확정 및 버전 관리 전환

운영 조사와 복원 실험이 통과한 다음 별도 변경으로 다음을 구현한다.

1. 실제 schema dump에서 비밀/환경 의존 사항을 정리한 초기 스키마를 버전 파일로 작성한다. baseline 버전은 **실제로 이미 적용된 변경까지만** 포함해야 한다. 적용하지 않은 2026-020을 baseline 처리해 누락시키지 않는다.
2. 기존 DB에는 명시적 Flyway `baseline`을 한 번 실행한다. 비어 있는 검증 DB는 전체 초기 스키마부터 `migrate`하도록 별도로 검증한다. `baselineOnMigrate=false`를 유지하여 잘못 연결한 비어 있지 않은 DB를 자동 승인하지 않는다. [Flyway baselineOnMigrate 문서](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting)
3. Spring Boot 4.1에 맞춰 `spring-boot-starter-flyway`와 `org.flywaydb:flyway-mysql`을 추가하고 검증된 SQL만 `src/main/resources/db/migration/V...__....sql`에 둔다. 이후 적용된 파일은 수정하지 않고 추가 버전을 만든다. Spring은 기본적으로 startup에서 migrate를 수행하므로 의존성 추가 자체도 운영 동작 변경이다. [Spring Boot DB 초기화 문서](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
4. 준비된 migration이 먼저 실행되고 JPA가 `validate`하도록 prod 설정을 바꾼다. migration 계정과 애플리케이션 계정 권한 분리를 검토한다. Flyway와 Hibernate `update`를 동시에 스키마 작성자로 두지 않는다.
5. CI에 같은 MySQL 버전으로 빈 DB 생성 → 전체 migrate → validate, 이전 baseline 복원 → migrate → validate, 재실행 무변경, checksum 변조 실패를 추가한다. H2 단위/통합 테스트는 계속 실행하되 MySQL 검증과 구분한다.
6. 운영 실행 창에서 백업/복원 지점, 쓰기 중지 필요 여부, DB lock 예상 시간을 정하고 실제 대상 확인 → migration 적용 → 이력/스키마 확인 → 새 앱 기동 → HTTP smoke를 순서대로 수행한다.

MySQL의 ALTER TABLE 등 DDL은 일반 트랜잭션 rollback으로 되돌리는 작업이 아니다. 실패 시 부분 적용 여부를 실제 스키마로 확인하고, 검토된 전진 수정 또는 검증된 백업 복원 절차를 따른다. 데이터가 있는 ENUM을 축소하거나 이력 행을 삭제하는 자동 down migration은 제공하지 않는다. [MySQL implicit commit 문서](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html)

## 완료 증거와 배포 준비 상태

다음 항목이 채워져야 `BLOG_SCHEMA_REVISION=2026-020`을 설정하고 이 배포 경로를 사용한다.

| 증거 | 필요한 기록 |
| --- | --- |
| 실제 운영 기준점 | 실행 SHA, MySQL 버전, schema hash, ddl-auto 실제 유효값, 기존 이력 |
| 백업 복원 | 백업 hash/시각, 별도 DB 복원 성공, 주요 데이터/무결성 비교 |
| 업그레이드 | 적용 SQL checksum/순서, 변경 전후 schema, 새 JAR validate/업무 회귀 결과 |
| 복구 호환성 | 새 글·댓글·예약 취소 이력 행을 포함한 DB에서 복구 대상 JAR 조회·기동 성공 |
| 운영 적용 | 대상 재확인, 실제 적용 이력/결과, HTTP/DB smoke, 복구 위치 |

현재 이 문서와 후보 SQL·조사 스크립트만 준비되었다. 실제 운영 schema, 복원/업그레이드, Flyway 이력 생성, validate 전환은 수행하지 않았다.
