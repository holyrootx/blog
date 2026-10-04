# 로컬 개발 환경 준비

이 문서는 개인 컴퓨터에서 MySQL `blog_local`과 개발용 R2 버킷을 준비하고, 공개 화면·관리자 화면·Google 소셜 로그인을 실행하는 순서입니다. 저장소의 기본 프로필은 `local`입니다. 운영 DB·버킷·계정을 사용하지 않습니다.

준비물은 Java 21, Node.js 22, npm, 실행 중인 로컬 MySQL 8.0 및 `mysql` 클라이언트, Cloudflare R2 계정입니다. Gradle은 저장소의 Wrapper를 사용합니다. Google 소셜 로그인은 마지막 절의 선택 설정입니다.

```sh
java -version
node --version
npm --version
mysql --version
```

## 1. 로컬 MySQL 데이터베이스와 계정

MySQL 관리자 계정으로 **로컬 MySQL**에 접속합니다. 아래는 관리자 이름이 `root`인 경우입니다. `-p` 뒤에 비밀번호를 붙이지 않고 프롬프트에서 입력합니다.

```sh
mysql -u root -p
```

MySQL 프롬프트에서 다음을 실행합니다. `replace-with-your-local-db-password`는 직접 정한 로컬 DB 비밀번호로 바꿉니다. SQL 문자열에 작은따옴표를 넣는 경우 이스케이프가 필요하므로, 초기 설정에는 비밀번호 관리자로 생성한 영문·숫자 위주의 충분히 긴 값을 사용할 수 있습니다.

```sql
CREATE DATABASE IF NOT EXISTS blog_local
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'blog_local'@'127.0.0.1'
    IDENTIFIED BY 'replace-with-your-local-db-password';

GRANT ALL PRIVILEGES ON blog_local.* TO 'blog_local'@'127.0.0.1';

SHOW GRANTS FOR 'blog_local'@'127.0.0.1';
EXIT;
```

애플리케이션 계정은 `127.0.0.1`에서만 접속하며 `blog_local` 데이터베이스에만 권한을 갖습니다. 기존에 같은 계정을 만들었다면 `CREATE USER`의 중복 오류를 확인하고 기존 계정을 사용합니다. 이 절차는 기존 계정의 비밀번호나 권한을 초기화하지 않습니다.

다음으로 애플리케이션 계정의 TCP 접속을 확인합니다.

```sh
mysql --protocol=TCP -h 127.0.0.1 -P 3306 -u blog_local -p blog_local
```

```sql
SELECT DATABASE(), CURRENT_USER();
EXIT;
```

결과는 `blog_local`과 `blog_local@127.0.0.1`이어야 합니다. 현재 `ddl-auto=update` 정책에 따라 첫 백엔드 기동 때 테이블이 생성됩니다. 오픈 전 DDL 확정·DB 재구성·데이터 재삽입·검증 후 `validate`로 전환하는 정책은 별도 작업입니다.

## 2. 개발용 R2 버킷과 키

현재 업로드 저장소는 R2입니다. 공개 글만 읽더라도 백엔드 기동에 R2 설정이 필요합니다. 개발용 버킷과 그 버킷에만 접근하는 키를 준비합니다.

1. Cloudflare 대시보드에서 R2를 활성화하고 개발용 버킷을 만듭니다. 계정의 R2 활성화 절차는 [R2 시작하기](https://developers.cloudflare.com/r2/get-started/)를 참고합니다.
2. R2 API 토큰 관리에서 **Object Read & Write** 권한을 선택하고, 접근 대상을 방금 만든 개발용 버킷으로 제한합니다. 발급 결과의 **Access Key ID**와 **Secret Access Key**를 보관합니다. Cloudflare 일반 API 토큰 문자열을 S3 secret 대신 넣지 않습니다. [R2 인증과 키 발급](https://developers.cloudflare.com/r2/api/tokens/)
3. 버킷에 표시된 S3 API 끝점을 확인합니다. 일반적인 형태는 `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`입니다. 앱에는 이 끝점과 버킷 이름을 따로 설정합니다.
4. 브라우저가 이미지를 읽을 공개 URL을 준비합니다. 개발 확인에는 버킷 설정의 **Public Development URL**을 활성화해 표시되는 `https://pub-….r2.dev`를 사용할 수 있습니다. `r2.dev`는 속도 제한이 있는 개발용 주소입니다. 운영용 공개 주소는 사용자 지정 도메인으로 구성합니다. [공개 버킷 설정](https://developers.cloudflare.com/r2/buckets/public-buckets/)

| 환경변수 | 넣을 값 |
| --- | --- |
| `R2_ENDPOINT` | 버킷의 S3 API 끝점 |
| `R2_BUCKET` | 개발용 버킷 이름 |
| `R2_REGION` | `auto` |
| `R2_ACCESS_KEY_ID` | 개발용 버킷에 제한한 Access Key ID |
| `R2_SECRET_ACCESS_KEY` | 같은 키의 Secret Access Key |
| `R2_PUBLIC_BASE_URL` | 공개 개발 URL 또는 사용자 지정 도메인 |

`R2_ENDPOINT`는 백엔드 업로드용 주소이고 `R2_PUBLIC_BASE_URL`은 브라우저 이미지 조회용 주소입니다. 서로 바꾸어 넣으면 업로드 또는 화면 표시가 실패합니다. 공개 URL로 제공하는 개발 버킷에는 공개해도 되는 테스트 이미지를 사용합니다.

## 3. 환경변수와 백엔드 실행

저장소 루트에서 예제 파일을 복사합니다. `cp -n`은 기존 `.env`를 덮어쓰지 않습니다.

```sh
cp -n examples/backend.env.example backend/.env
chmod 600 backend/.env
```

`backend/.env`를 편집하여 DB 비밀번호와 R2 값을 채웁니다. 변수 값은 작은따옴표로 감싼 셸 문법입니다. 앞 절에서 만든 DB 이름과 계정 이름은 모두 `blog_local`입니다. 파일은 Git에서 제외되며 실제 값은 커밋하지 않습니다.

`.env`를 자동으로 읽는 기능은 없으므로 같은 터미널에서 환경변수로 내보낸 후 기동합니다.

```sh
cd backend
set -a
source .env
set +a
./gradlew bootRun
```

예제의 `SPRING_PROFILES_ACTIVE=local`, `SERVER_ADDRESS=127.0.0.1`을 유지하면 로컬 프로필로 루프백 주소의 8080 포트에 기동합니다. `Started BlogApplication` 로그가 나타나면 다른 터미널에서 확인합니다.

```sh
curl --fail http://127.0.0.1:8080/api/health
```

이 응답은 애플리케이션의 HTTP 응답 확인입니다. DB·R2 기능까지 확인하려면 관리자 로그인과 글 저장, 이미지 업로드를 이어서 실행합니다.

## 4. 프론트엔드 실행

새 터미널의 저장소 루트에서 실행합니다.

```sh
cd frontend
npm ci
npm run dev
```

브라우저에서 [http://localhost:5173](http://localhost:5173)을 엽니다. Vite는 `/api`, `/uploads`, `/oauth2`, `/login/oauth2` 요청을 `http://127.0.0.1:8080`으로 전달합니다. 처음에는 게시글과 카테고리가 없는 상태이며, 관리자 계정을 준비한 뒤 콘텐츠를 만들 수 있습니다.

## 5. 로컬 관리자 계정 생성

관리자 계정은 자동 생성되지 않습니다. 첫 백엔드 기동으로 `member` 테이블이 생성된 후, 로컬 DB에 초기 관리자 한 명을 넣습니다. 관리자 비밀번호는 애플리케이션과 같은 `BCryptPasswordEncoder`로 해시를 생성합니다.

아래 블록은 **새 터미널에서 저장소의 `backend` 디렉터리로 이동한 뒤** 실행합니다. 임시 Gradle 설정으로 실제 프로젝트의 의존성 classpath를 구하고 Java 프로그램을 실행합니다. 비밀번호는 터미널의 숨김 입력으로 받으며 명령 인자로 전달하지 않습니다. 임시 파일은 실행이 끝나면 삭제됩니다.

```sh
(
  set -eu
  BLOG_HASH_TMP="$(mktemp -d)"
  trap 'rm -rf "$BLOG_HASH_TMP"' EXIT

  cat > "$BLOG_HASH_TMP/local-password.init.gradle" <<'GRADLE'
allprojects {
    afterEvaluate {
        tasks.register('printLocalPasswordClasspath') {
            doLast {
                println configurations.runtimeClasspath.asPath
            }
        }
    }
}
GRADLE

  cat > "$BLOG_HASH_TMP/LocalAdminPassword.java" <<'JAVA'
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LocalAdminPassword {
    public static void main(String[] args) {
        var console = System.console();
        if (console == null) {
            throw new IllegalStateException("터미널에서 직접 실행하세요.");
        }
        char[] first = console.readPassword("로컬 관리자 비밀번호: ");
        char[] second = console.readPassword("비밀번호 확인: ");
        try {
            if (first == null || second == null || !Arrays.equals(first, second)) {
                throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
            }
            var password = new String(first);
            if (password.isBlank()
                    || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalArgumentException("빈 값은 사용할 수 없으며 UTF-8 72바이트 이하여야 합니다.");
            }
            console.printf("%npassword_hash에 넣을 값:%n%s%n",
                    new BCryptPasswordEncoder().encode(password));
        } finally {
            if (first != null) Arrays.fill(first, '\0');
            if (second != null) Arrays.fill(second, '\0');
        }
    }
}
JAVA

  BLOG_HASH_CLASSPATH="$(./gradlew --quiet --console=plain \
    --init-script "$BLOG_HASH_TMP/local-password.init.gradle" \
    printLocalPasswordClasspath)"
  java --class-path "$BLOG_HASH_CLASSPATH" "$BLOG_HASH_TMP/LocalAdminPassword.java"
)
```

출력된 `$2a$…` 형태의 해시 전체를 복사합니다. 이 프로젝트는 `BCryptPasswordEncoder`를 직접 사용하므로 `{bcrypt}` 같은 접두어를 추가하지 않습니다.

다시 로컬 애플리케이션 DB 계정으로 접속합니다.

```sh
mysql --protocol=TCP -h 127.0.0.1 -P 3306 -u blog_local -p blog_local
```

먼저 연결 대상과 동일한 관리자 이름이 있는지 확인합니다.

```sql
SELECT DATABASE(), CURRENT_USER();
SELECT id, username, role, status FROM member WHERE username = 'local-admin';
```

`blog_local`에 연결되어 있고 조회 결과가 없으면, 아래 `<BCrypt 해시 전체>`를 생성한 해시로 바꿔 실행합니다. 비밀번호 원문을 넣지 않습니다. 이미 존재하는 `local-admin` 행은 그대로 사용하며 이 INSERT를 반복하지 않습니다.

```sql
INSERT INTO member (
    role, provider, username, password_hash, nickname, status,
    created_at, updated_at, created_by, updated_by
) VALUES (
    'ADMIN', 'LOCAL', 'local-admin', '<BCrypt 해시 전체>', '로컬 관리자', 'ACTIVE',
    NOW(6), NOW(6), 'local-setup', 'local-setup'
);

SELECT id, username, role, provider, status
FROM member WHERE username = 'local-admin';
EXIT;
```

`id`는 자동 증가하고, 소셜 식별자·이메일·프로필 이미지는 이 관리자에게 필요하지 않습니다. [관리자 로그인](http://localhost:5173/admin/login)에서 `local-admin`과 해시 생성 때 입력한 비밀번호로 로그인합니다. 관리자 세션은 마지막 활동 이후 2시간 정책을 사용합니다.

## 6. Google 소셜 로그인 추가 설정

이 절은 회원·댓글 기능을 실제 소셜 로그인으로 확인할 때 진행합니다. 기본 `local` 설정에는 OAuth 클라이언트 등록이 없으므로 Google 환경변수 두 개만 추가하면 활성화되지 않습니다.

1. Google Cloud에서 개발용 OAuth 클라이언트와 동의 화면을 준비합니다. 앱 유형은 웹 애플리케이션을 선택하고, 필요한 경우 본인 Google 계정을 테스트 사용자로 등록합니다. [Google 웹 서버 OAuth 안내](https://developers.google.com/identity/protocols/oauth2/web-server)
2. 승인된 리디렉션 URI에 다음 주소를 정확히 등록합니다.

   ```text
   http://localhost:5173/login/oauth2/code/google
   ```

3. 저장소 루트에서 추가 설정 예제를 복사합니다.

   ```sh
   cp -n examples/application-local.example.properties backend/application-local.properties
   ```

4. `backend/.env`의 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` 주석을 풀고 개발용 클라이언트 값으로 바꿉니다. `application-local.properties`는 이 환경변수를 참조하므로 파일 안에 실제 키를 적을 필요가 없습니다.
5. 기존 백엔드를 `Ctrl+C`로 종료하고, `backend` 디렉터리의 터미널에서 환경변수를 다시 읽은 뒤 실행합니다.

   ```sh
   set -a
   source .env
   set +a
   ./gradlew bootRun \
     --args='--spring.profiles.active=local --spring.config.additional-location=file:./application-local.properties'
   ```

`application-local.properties`는 Spring Boot가 현재 실행 디렉터리에서도 발견할 수 있는 파일입니다. 위 명령은 추가 파일 위치를 명시해 어떤 설정을 사용하는지 분명하게 합니다. 이 파일과 `.env`는 Git에서 제외됩니다.

이 예제는 **Google만 등록**합니다. [Google 로그인 시작](http://localhost:5173/oauth2/authorization/google)으로 이동하거나 사이트의 Google 로그인 버튼을 사용합니다. Kakao·Naver는 별도의 등록 설정과 개발용 키를 준비해야 사용할 수 있습니다.

콜백은 Vite의 `/login/oauth2` 프록시를 거쳐 백엔드에서 처리되고, 결과는 `http://localhost:5173/oauth/callback`으로 돌아옵니다. 접속과 Google 등록 주소 모두 `localhost:5173`으로 통일합니다. `127.0.0.1` 주소와 섞으면 브라우저의 세션 쿠키가 공유되지 않습니다.

설정 예제의 `scope=profile,email`을 유지합니다. 현재 회원 연동은 일반 OAuth2 사용자 서비스 기준이므로 `openid`를 추가하면 다른 인증 처리 경로를 사용하게 됩니다.

## 7. 실행 결과 확인

- 공개 화면에서 카테고리·글 목록이 표시되거나 정상적인 빈 목록 상태가 나오는지 확인합니다.
- 로컬 관리자로 카테고리와 임시 글을 만들고, 새로고침 뒤에도 저장 결과가 유지되는지 확인합니다.
- 테스트 이미지 한 장을 업로드하고, 공개 이미지 URL로 표시되는지 확인합니다. R2 키와 공개 주소 설정은 이 단계에서 함께 검증됩니다.
- Google을 설정했다면 가입·로그인·댓글 작성·로그아웃이 동작하는지 확인합니다.

`./gradlew test`의 기본 DB는 H2이며, 프론트엔드 브라우저 테스트는 API 응답을 대체합니다. 이 자동 테스트 결과와 위의 실제 MySQL·R2·Google 연결 확인은 각각 따로 확인해야 합니다. 일반 로컬 테스트에서는 `TRASH_TEST_DB_*` 환경변수를 설정하지 않습니다. 해당 선택 기능은 지정한 DB에서 테이블을 생성·삭제하는 테스트용입니다.
