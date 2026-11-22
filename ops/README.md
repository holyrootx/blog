# 테스트·배포 운영 계약

저장소에 있는 로컬 검사, 배포 워크플로우, 서버 복구 절차를 설명한다. 운영 서버·DB·GitHub 설정의 적용 상태는 대상 릴리스에서 확인한다.

테스트·의존성 잠금 파일·비밀값 없는 테스트 설정·워크플로우·스크립트는 코드와 함께 관리한다. 운영 `.env`, 개인 설정, 원본 증적과 키는 로컬에서 보관하고, 커밋 전 공개할 경로를 확인한다.

## 로컬 Mac / 깨끗한 checkout

Java 21, Node.js 22.13.0 이상의 22.x, Python 3, Git으로 아래를 실행한다. Playwright 브라우저는 OS마다 설치한다. Linux CI는 `--with-deps`를 사용한다. H2 테스트는 실제 MySQL 마이그레이션 검증을 대체하지 않는다.

```bash
cd backend
./gradlew --no-daemon clean test bootJar
cd ../frontend
npm ci
npx playwright install chromium
npm test
npm run test:browser
npm run build
cd ..
python3 -m unittest discover -s scripts/tests -p '*_test.py'
bash -n scripts/deploy-release.sh
bash -n scripts/inspect-database.sh
```

`checks.yml`은 develop/release PR과 개발 브랜치 push에서 같은 테스트·빌드를 수행한다. `deploy.yml`은 동일 reusable workflow가 모두 통과한 뒤 **그 실행에서 나온 산출물**을 다운로드한다. 배포 단계에서 테스트와 다른 코드를 다시 빌드하지 않는다. 테스트 리포트와 산출물은 Actions에 14일 보관한다.

워크플로우는 `Test and build`와 `MySQL transactions and restart` 결과를 보고한다. 병합 허용을 판단할 때는 대상 PR의 검사 결과를 확인한다. GitHub가 병합을 제한하는 조건과 직접 푸시 허용 여부는 해당 브랜치의 보호 규칙에서 별도로 확인한다. [GitHub 보호 브랜치 문서](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)

## 배포 활성화 전 한 번 확인할 사항

| 구분 | 필요 값/확인 | 동작 또는 확인 기준 |
| --- | --- | --- |
| Secrets | `LIGHTSAIL_HOST`, `LIGHTSAIL_USER`, `LIGHTSAIL_SSH_KEY` | 호스트·사용자 형식과 키 존재 여부를 검사 |
| Secret | `LIGHTSAIL_KNOWN_HOSTS` | 별도 신뢰 경로에서 확인한 실제 호스트의 OpenSSH known_hosts 행. 정확한 호스트 이름/IP 또는 해시 항목 포함 |
| Variable | `BLOG_PUBLIC_BASE_URL` | 실제 검증된 HTTPS origin. 경로·후행 `/` 없는 값. 예: `https://jsjlog.me` |
| Variable | `BLOG_SCHEMA_REVISION=2026-020` | 아래 DB 준비 및 이전 버전 호환성 검증 완료 후 한 번 설정. 자동 DB 검사의 증거가 아닌 운영자가 기록하는 준비 상태 |
| 서버 명령 | Bash, Python 3, `rsync`, `flock`, `sha256sum`, `systemctl`, 비대화형 sudo | 서버에서 존재·권한 확인. 테스트는 sudo 권한을 부여하지 않음 |
| 기존 경로 | `/home/ubuntu/apps/blog/backend/app.jar`, `/var/www/blog/index.html`, systemd `blog-backend` | 기존 설치가 있어야 하며 새 서버 초기 설치는 이 절차 범위 밖 |
| DB 호환성 | 실제 2026-020 스키마·복원/업그레이드/이전 JAR 동작 | [DB 전환안](database/README.md) 참조 |
| 공개 경로 | HTML의 앱 JS/CSS/modulepreload 참조 및 `deployment.json`이 실제 새 버전을 가리키는지 | 로컬 index 파일과 앱 자산 참조를 비교하고 버전을 검사. CDN의 외부 script 삽입은 허용하지만 앱 자산 누락·다른 경로·오래된 캐시는 거절 |
| 저장 공간 | 새 JAR/정적 파일 + 현재 버전 전체 스냅샷의 여유 공간 | 오래된 릴리스를 자동 삭제하지 않으므로 별도 보관 주기 필요 |

`ssh-keyscan`은 네트워크에서 받은 키가 신뢰할 서버의 키인지 증명하지 않는다. 서버 콘솔 등 이미 신뢰하는 경로에서 fingerprint를 대조하고 known_hosts 값을 등록한다. 워크플로우는 새 키를 자동 수집/수락하지 않으며 `StrictHostKeyChecking=yes`를 사용한다. [OpenSSH ssh-keyscan 문서](https://man.openbsd.org/ssh-keyscan)

`BLOG_SCHEMA_REVISION`은 매 커밋마다 승인받는 장치가 아니다. 이 릴리스가 요구하는 스키마와 복구 호환성을 실제로 확인할 때까지 업로드를 막는 초기 조건이다. 후속 스키마 요구가 달라지면 요구 버전과 검증 절차를 함께 갱신한다.

## 서버 배포의 순서와 보관 위치

HTML 캐시 검사는 `/`와 `/oauth/callback`의 `Cache-Control: no-store` 및 앱 자산 참조를 확인한다. 최초 적용 전에 기존 Nginx 설정을 백업하고 HTTPS server의 기존 SPA·루트 location을 `scripts/nginx/blog-spa-routes.conf` 내용으로 교체한 뒤 `nginx -t`와 reload를 수행한다. 해당 설정이 include하는 `ops/nginx/blog-security-headers.conf`는 `/etc/nginx/snippets/blog-security-headers.conf`에 있어야 한다. Nginx 설정은 릴리스 파일 배포가 자동 교체하지 않으므로 이 사전 적용을 누락하면 배포 전 검사에서 중단된다.

1. GitHub Actions가 `release` push를 받고 테스트/빌드를 완료한다. 배포 concurrency group은 한 번에 하나만 실행하고 진행 중 실행을 취소하지 않는다. 기본 GitHub queue는 대기 실행을 최신 실행으로 교체할 수 있어 모든 중간 SHA가 순서대로 배포된다는 의미는 아니다. [GitHub concurrency 문서](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)
2. 검증된 키로 `releases/incoming/<sha>-<run>-<attempt>.tar.gz`를 업로드한다. 별도 디렉터리로 풀고 모든 파일의 SHA-256 manifest를 검증한다.
3. 서버의 `.deploy.lock`으로 수동 배포까지 직렬화한다. 기존 설치의 로컬 HTTP/DB 조회·공개 HTML/JS가 정상인지 먼저 확인하고 `--index-file`로 현재 설치의 앱 자산 참조와 비교한다.
4. 현재 JAR와 정적 파일 전체를 새 릴리스의 `rollback/`에 보관한다. `.env`, Nginx, systemd 설정은 건드리지 않는다.
5. 기존 `backend/app.jar`를 임시 파일+rename으로 교체한다. 기존 `/var/www/blog` 경로에는 새 정적 파일을 rsync한다. 정적 파일 교체 전체가 원자적이라는 보장은 없으며 짧은 전환 구간이 존재한다.
6. systemd를 재시작하고 반복 HTTP 검증을 한다. `/api/health`는 프로세스 확인이고 `/api/v1/blog/posts?size=1`은 실제 DB 조회다. `--index-file`로 새 index의 앱 JS/CSS/modulepreload URL과 공개 HTML의 참조를 비교하고, 같은 origin의 앱 JS 응답 MIME/비어 있지 않은 본문과 `deployment.json`의 릴리스 ID를 확인한다. 요청은 `jsjlog-deployment-check/1.0` 식별 User-Agent를 사용한다.
7. 성공 시 해당 릴리스의 `status=ACTIVE`, `releases/current-release`에 ID를 기록한다. 파일 교체 중 또는 교체 뒤 오류를 복구 절차가 처리하면 이전 JAR와 정적 파일을 복원·재시작·재검증하고 작업은 실패로 끝난다. 복구 성공은 `ROLLED_BACK`, 복구 실패는 `ROLLBACK_FAILED`다.

공개 HTML 검사는 파일 전체의 바이트 동일성 대신 앱 자산 참조 동일성을 검사한다. 상대 URL·기본 포트·fragment를 정규화하며 `<base>`도 반영한다. Cloudflare가 추가하는 외부 beacon script와 HTML 공백 변화는 허용한다. 원본에 있던 외부 앱 자산의 누락, 같은 origin의 추가 script, CSS/modulepreload 변경은 실패한다. HTML 본문 문구나 inline script 전체의 무결성을 보증하는 검사는 아니다. 산출물 파일 자체의 SHA-256 manifest 검증은 그대로 유지한다.

## 실행 상태에 따른 대응과 인수

Actions에서 대상 커밋 SHA, run ID, attempt와 실패한 job·step을 확인하고 기록한다. `Deploy Blog`의 릴리스 ID는 `<sha>-<run>-<attempt>` 형식이다. 같은 SHA라도 attempt가 다르면 이전 실행의 상태 파일이나 백업을 현재 실행의 것으로 해석하지 않는다.

| 실행 상태 | 확인과 다음 조치 |
| --- | --- |
| **PR·개발 브랜치 push의 Checks 실패** | 대상 SHA와 실패한 job·step을 확인한다. 원인이 불명확하거나 코드·설정 오류라면 조사부터 한다. 외부 의존성 요청 제한처럼 일시 장애가 로그에서 확인됐고 같은 SHA가 여전히 검사 대상이면, 진행 중인 중복 검사가 없는지 확인한 뒤 실패한 작업만 재검사한다. |
| **release의 Deploy Blog에서 Checks 실패** | 해당 시도의 `deploy`는 Checks가 성공해야 시작한다. [실패 작업 재실행](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs)은 원래 SHA를 사용하고, 검사가 통과하면 이에 의존하는 `deploy`까지 이어져 실제 운영 배포를 재개할 수 있다. 일시 장애가 로그에서 확인되고 정확한 SHA·run·attempt와 진행 중이거나 대기 중인 배포를 대조한 뒤, 해당 SHA의 운영 배포를 재개할 때만 실행한다. 원인이 불명확하면 조사부터 한다. |
| **deploy 실행 중 또는 활성화 실패** | 실행 중에는 새 배포나 수동 복구를 겹쳐 실행하지 않는다. 실패하면 로그에서 산출물 확인·SSH·서버 사전 검사·파일 교체 중 어디까지 진행됐는지 본다. 교체 전에 멈춘 것이 확인되면 해당 단계의 원인을 해결한다. 교체 여부가 확실하지 않으면 정확한 릴리스 ID의 `status`, `rollback/`, 현재 서비스와 설치 리비전을 대조한다. `PREPARED`는 백업을 남긴 시점이며, 이 값만으로 교체 여부를 판단할 수 없다. |
| **복구 결과** | `ROLLED_BACK`이면 이전 파일을 복원하고 재검사한 결과다. 대상 리비전의 배포는 실패로 기록한다. `ROLLBACK_FAILED`, `ROLLING_BACK`이 남은 채 실행이 끊긴 경우, 또는 상태와 실제 서비스가 맞지 않는 경우는 백업과 서비스 상태를 조사한 뒤 [서버 복구 절차](#서버에서-복구가-필요한-경우)를 판단한다. |
| **워크플로우 성공 후 운영 인수** | 그 run에서 사용한 `verified-build` 산출물과 실행 중인 JAR·릴리스 manifest, `ACTIVE` 상태와 현재 리비전을 대조한다. 상태 API·DB 조회·공개 HTML과 앱 자산·배포 식별자를 확인하고, 변경된 사용자 동작은 별도로 확인한다. 워크플로우 성공과 운영 인수 결과는 나눠 기록한다. |

## 서버에서 복구가 필요한 경우

자동 복구는 파일 교체에 앞서 오류와 INT/TERM 처리기를 설정한다. 프로세스 강제 종료, 연결 단절, 호스트 정지에는 완료가 보장되지 않는다. **실패한 실행에 대응하는 정확한 릴리스 ID**의 `status`, `rollback/`과 현재 서비스 상태부터 확인한다. 수동 복구는 그 백업으로 이전 앱 파일을 복원하는 절차이며, 같은 커밋 SHA의 자동 배포 재실행과 별개다.

다음은 운영자가 해당 ID와 DB 호환성을 확인한 후 실행할 명령이다. `release_id`는 실제 값으로 지정한다. DB 복원 명령은 포함하지 않으며, 서비스 파일 복구가 DB를 되돌린다고 해석하지 않는다.

```bash
(
set -euo pipefail
release_id='<failed-sha-run-attempt>'
app_root=/home/ubuntu/apps/blog
backup_dir="$app_root/releases/$release_id/rollback"
test -f "$backup_dir/app.jar"
test -f "$backup_dir/frontend/index.html"
revision_args=()
if [[ -f "$backup_dir/frontend/deployment.json" ]]; then
  previous_revision=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["revision"])' "$backup_dir/frontend/deployment.json")
  test -n "$previous_revision"
  revision_args=(--revision "$previous_revision")
fi
exec 9>"$app_root/.deploy.lock"
flock -w 300 9
cp -p "$backup_dir/app.jar" "$app_root/backend/.app.jar.rollback"
mv -f "$app_root/backend/.app.jar.rollback" "$app_root/backend/app.jar"
sudo -n rsync -a --delete "$backup_dir/frontend/" /var/www/blog/
sudo -n systemctl restart blog-backend
python3 "$app_root/releases/$release_id/scripts/check-deployment.py" \
  --backend http://127.0.0.1:8080 --public https://jsjlog.me \
  --index-file "$backup_dir/frontend/index.html" \
  "${revision_args[@]}"
sudo systemctl status blog-backend --no-pager
)
```

괄호 안의 subshell이 끝나면 복구 잠금도 해제된다. 이전 버전의 `deployment.json`이 있으면 그 revision까지 검사하고, 최초 전환처럼 없으면 이전 index의 앱 자산 참조로 확인한다. 이 수동 절차는 release의 상태 파일이나 `current-release` 기록을 자동 수정하지 않는다. 복구 결과와 실제 설치 revision을 확인한 뒤 운영 기록을 맞춘다.

새 스키마와 신규 행을 이전 JAR가 처리할 수 없으면 파일 복구만으로 안전하지 않다. 첫 적용 전에 복원 DB에서 이전 JAR의 기동·조회·이력 처리를 검증하여 호환 가능한 이전 산출물을 준비한다. DB 복원은 쓰기를 멈춘 상태에서 별도 승인·복구 지점/데이터 손실 검토가 필요하다.

## 2026-10-03 읽기 전용 관측

이 날짜에 기존 SSH 인증, 서비스 `active`, 배포 경로·필수 명령·비대화형 sudo, 환경 파일의 필수 키 존재를 확인했다. 당시 MySQL 8.0.46에서 `post.deleted_at`과 `content_history`가 후보 SQL과 일치한다고 기록했다. 당시 운영 버전의 내부 API·DB 조회·공개 HTML 앱 자산·JS 응답도 확인했다. 검사기는 기본 Python User-Agent 거절과 Cloudflare의 외부 분석 스크립트 삽입에 맞춰 보완했다.

`LIGHTSAIL_KNOWN_HOSTS`, `BLOG_PUBLIC_BASE_URL`, `BLOG_SCHEMA_REVISION` 미등록은 그날의 관측이다. 그 관측으로 당시 신규 릴리스의 배포·복구, 기존 백업 방식, runtime override 전체를 확인하지는 않았다. 현재 설정과 인수 결과는 해당 실행의 기록에서 확인한다.

## 운영 참고

댓글 작성 제한은 애플리케이션의 회원별 메모리 카운터다. `blog.comments.writes-per-minute` 기본 10회이고, 재시작 시 초기화되며 단일 인스턴스에만 공유된다. Cloudflare의 별도 제한 여부는 미확인이다. 여러 인스턴스로 확장하면 공유 저장소/게이트웨이 제한이 필요하다.
