# JSJ.log

새로 시작하는 블로그 프로젝트입니다.

## 구조

- `backend`: Spring Boot API server
- `frontend`: Vue 3 + Vite public/admin client

## 실행

백엔드:

```bash
cd backend
./gradlew bootRun
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
