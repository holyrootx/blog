# 테스트·배포 운영 계약

이 문서는 2026-10-03 개발·보안·QA 인계 항목을 코드로 반영하기 위한 변경안이다. 운영 서버·DB·GitHub 설정에 적용했다는 기록이 아니다. 기존 미추적 `docs/`, `.claude/`, `CLAUDE.md`는 보존한다.

이번에 요청된 테스트/CI 연결 범위에서 `backend/src/test/**`, `frontend/tests/**`, 테스트 의존성/잠금 파일, 비밀 없는 H2 설정, `.github/**`, `scripts/**`, `ops/**`를 코드와 함께 관리한다. 로컬 `docs/DEVELOPMENT.md`의 과거 “테스트 커밋 제외” 결정은 이 범위에 적용하지 않는다. 운영 `.env`, 개인 YAML fallback, 증거 덤프와 키는 포함하지 않는다. 전체 `git add .` 대신 검토한 경로만 지정한다.

## 로컬 Mac / 깨끗한 checkout

Java 21, Node 22, Python 3, Git으로 아래를 실행한다. Playwright 브라우저는 OS마다 설치한다. Linux CI는 `--with-deps`를 사용한다. H2 테스트는 실제 MySQL 마이그레이션 검증을 대체하지 않는다.

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
bash -n scripts/deploy-release.sh scripts/inspect-database.sh
```

`checks.yml`은 develop/release PR과 개발 브랜치 push에서 같은 테스트·빌드를 수행한다. `deploy.yml`은 동일 reusable workflow가 모두 통과한 뒤 **그 실행에서 나온 산출물**을 다운로드한다. 배포 단계에서 테스트와 다른 코드를 다시 빌드하지 않는다. 테스트 리포트와 산출물은 Actions에 14일 보관한다.

PR 병합을 강제 차단하려면 GitHub의 develop/release 보호 규칙에서 `Checks / Test and build`에 해당하는 실제 첫 실행의 check 이름을 required status check로 지정하고 직접 push 우회 정책을 설정해야 한다. workflow 파일만으로 원격 보호 규칙은 바뀌지 않는다. [GitHub 보호 브랜치 문서](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)

## 배포 활성화 전 한 번 확인할 사항

| 구분 | 필요 값/확인 | 현재 변경안의 동작 |
| --- | --- | --- |
| 기존 Secrets | `LIGHTSAIL_HOST`, `LIGHTSAIL_USER`, `LIGHTSAIL_SSH_KEY` | 비어 있거나 허용 형식이 아니면 중단 |
| 신규 Secret | `LIGHTSAIL_KNOWN_HOSTS` | 별도 신뢰 경로에서 확인한 실제 호스트의 OpenSSH known_hosts 행. 정확한 호스트 이름/IP 또는 해시 항목 포함 |
| 신규 Variable | `BLOG_PUBLIC_BASE_URL` | 실제 검증된 HTTPS origin. 경로·후행 `/` 없는 값. 예: `https://jsjlog.me` |
| 신규 Variable | `BLOG_SCHEMA_REVISION=2026-020` | 아래 DB 준비 및 이전 버전 호환성 검증 완료 후 한 번 설정. 자동 DB 검사의 증거가 아닌 운영자가 기록하는 준비 상태 |
| 서버 명령 | Bash, Python 3, `rsync`, `flock`, `sha256sum`, `systemctl`, 비대화형 sudo | 서버에서 존재·권한 확인. 테스트는 sudo 권한을 부여하지 않음 |
| 기존 경로 | `/home/ubuntu/apps/blog/backend/app.jar`, `/var/www/blog/index.html`, systemd `blog-backend` | 기존 설치가 있어야 하며 새 서버 초기 설치는 이 절차 범위 밖 |
| DB 호환성 | 실제 2026-020 스키마·복원/업그레이드/이전 JAR 동작 | [DB 전환안](database/README.md) 참조 |
| 공개 경로 | HTML/JS 및 `deployment.json`이 실제 새 정적 파일을 반환하는지 | HTML 바이트 SHA와 버전 모두 검사. CDN HTML 변형/삽입·오래된 캐시가 있으면 실패하므로 사전 확인 |
| 저장 공간 | 새 JAR/정적 파일 + 현재 버전 전체 스냅샷의 여유 공간 | 오래된 릴리스를 자동 삭제하지 않으므로 별도 보관 주기 필요 |

`ssh-keyscan`은 네트워크에서 받은 키가 신뢰할 서버의 키인지 증명하지 않는다. 서버 콘솔 등 이미 신뢰하는 경로에서 fingerprint를 대조하고 known_hosts 값을 등록한다. 워크플로우는 새 키를 자동 수집/수락하지 않으며 `StrictHostKeyChecking=yes`를 사용한다. [OpenSSH ssh-keyscan 문서](https://man.openbsd.org/ssh-keyscan)

`BLOG_SCHEMA_REVISION`은 매 커밋마다 승인받는 장치가 아니다. 이 릴리스가 요구하는 스키마와 복구 호환성을 실제로 확인할 때까지 업로드를 막는 초기 조건이다. 후속 스키마 요구가 달라지면 요구 버전과 검증 절차를 함께 갱신한다.

## 서버 배포의 순서와 보관 위치

1. GitHub Actions가 `release` push를 받고 테스트/빌드를 완료한다. 배포 concurrency group은 한 번에 하나만 실행하고 진행 중 실행을 취소하지 않는다. 기본 GitHub queue는 대기 실행을 최신 실행으로 교체할 수 있어 모든 중간 SHA가 순서대로 배포된다는 의미는 아니다. [GitHub concurrency 문서](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)
2. 검증된 키로 `releases/incoming/<sha>-<run>-<attempt>.tar.gz`를 업로드한다. 별도 디렉터리로 풀고 모든 파일의 SHA-256 manifest를 검증한다.
3. 서버의 `.deploy.lock`으로 수동 배포까지 직렬화한다. 기존 설치의 로컬 HTTP/DB 조회·공개 HTML/JS가 정상인지 먼저 확인한다.
4. 현재 JAR와 정적 파일 전체를 새 릴리스의 `rollback/`에 보관한다. `.env`, Nginx, systemd 설정은 건드리지 않는다.
5. 기존 `backend/app.jar`를 임시 파일+rename으로 교체한다. 기존 `/var/www/blog` 경로에는 새 정적 파일을 rsync한다. 정적 파일 교체 전체가 원자적이라는 보장은 없으며 짧은 전환 구간이 존재한다.
6. systemd를 재시작하고 반복 HTTP 검증을 한다. `/api/health`는 프로세스 확인이고 `/api/v1/blog/posts?size=1`은 실제 DB 조회다. 공개 HTML의 SHA, 참조 JS의 MIME/내용, `deployment.json`의 릴리스 ID까지 확인한다.
7. 성공 시 해당 릴리스의 `status=ACTIVE`, `releases/current-release`에 ID를 기록한다. 실패하면 이전 JAR/정적 파일을 복원·재시작·재검증하고 job은 실패로 끝난다. 복구 성공은 `ROLLED_BACK`, 복구 실패는 `ROLLBACK_FAILED`다.

## 서버에서 복구가 필요한 경우

자동 복구는 일반 명령 실패와 INT/TERM을 처리한다. 프로세스 강제 종료, 연결 단절, 호스트 정지에는 완료가 보장되지 않는다. 실패한 release의 `status`, `rollback/` 존재와 현재 서비스 상태부터 확인한다. 임의의 최신 폴더가 아니라 **실패한 실행에 대응하는 정확한 ID**를 사용한다.

다음은 운영자가 해당 ID와 DB 호환성을 확인한 후 실행할 명령이다. `release_id`는 실제 값으로 지정한다. DB 복원 명령은 포함하지 않으며, 서비스 파일 복구가 DB를 되돌린다고 해석하지 않는다.

```bash
release_id='<failed-sha-run-attempt>'
app_root=/home/ubuntu/apps/blog
backup_dir="$app_root/releases/$release_id/rollback"
test -f "$backup_dir/app.jar"
test -f "$backup_dir/frontend/index.html"
exec 9>"$app_root/.deploy.lock"
flock -w 300 9
cp -p "$backup_dir/app.jar" "$app_root/backend/.app.jar.rollback"
mv -f "$app_root/backend/.app.jar.rollback" "$app_root/backend/app.jar"
sudo -n rsync -a --delete "$backup_dir/frontend/" /var/www/blog/
sudo -n systemctl restart blog-backend
python3 "$app_root/releases/$release_id/scripts/check-deployment.py" \
  --backend http://127.0.0.1:8080 --public https://jsjlog.me
sudo systemctl status blog-backend --no-pager
```

새 스키마와 신규 행을 이전 JAR가 처리할 수 없으면 파일 복구만으로 안전하지 않다. 첫 적용 전에 복원 DB에서 이전 JAR의 기동·조회·이력 처리를 검증하여 호환 가능한 이전 산출물을 준비한다. DB 복원은 쓰기를 멈춘 상태에서 별도 승인·복구 지점/데이터 손실 검토가 필요하다.

## 남은 운영 검증

실제 서버 실행 SHA, SSH 신뢰 키, DB schema/history/환경 override, systemd/Nginx 경로·권한, Cloudflare 캐시/헤더, 기존 백업·복원 수단은 이 변경에서 확인하거나 변경하지 않았다. 현재 서버에 백업 수단이 없다고 단정하지 않는다.

댓글 작성 제한은 애플리케이션의 회원별 메모리 카운터다. `blog.comments.writes-per-minute` 기본 10회이고, 재시작 시 초기화되며 단일 인스턴스에만 공유된다. Cloudflare의 별도 제한 여부는 미확인이다. 여러 인스턴스로 확장하면 공유 저장소/게이트웨이 제한이 필요하다.
