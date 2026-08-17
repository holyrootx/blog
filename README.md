# JSJ.log

새로 시작하는 블로그 프로젝트입니다.

## 구조

- `backend`: Spring Boot API server
- `frontend`: Vue 3 + Vite public/admin client

## 사전 준비물

- Java 21
- Node 22
- MySQL — 로컬에 `blog` 데이터베이스를 직접 생성해야 합니다. JPA(`ddl-auto=update`)가 테이블은 자동으로 만들어주지만 데이터베이스 자체는 만들어주지 않습니다.

```sql
CREATE DATABASE blog;
```

## 실행

백엔드:

```bash
cd backend
./gradlew bootRun
```

정상 기동 확인:

```bash
curl http://localhost:8080/api/health
```

프론트:

```bash
cd frontend
npm install
npm run dev
```

## 초기 방향

- 공개 블로그 화면은 mock data로 먼저 그립니다.
- 통계, 인기글, 최근 글, 프로필, 메인 대문 문구는 나중에 DB/API로 교체합니다.
- 관리자 페이지는 공개 블로그 레이아웃과 분리해서 진행합니다.
