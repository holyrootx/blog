import { getApiData, onCsrfRejected, setCsrfToken } from './blogApiClient';

// 관리자와 회원이 같은 세션 기반 CSRF 정책을 사용한다.
const CSRF_PATH = '/api/v1/auth/csrf';

/**
 * CSRF 토큰을 받아 클라이언트에 심는다.
 *
 * 로그인 전에 한 번, 로그인·로그아웃 직후에 다시 불러야 한다.
 * 서버가 그때마다 토큰을 새로 발급하기 때문이다 — 옛 토큰을 계속 쓰면 첫 저장이 403 이 된다.
 *
 * 관리자 화면만 쓰던 코드인데 회원 가입 화면이 두 번째 사용처가 되어 여기로 옮겼다.
 */
export async function refreshCsrfToken() {
  const token = await getApiData(CSRF_PATH);

  setCsrfToken(token);

  return token;
}

// 토큰이 어긋나 403 이 났을 때 클라이언트가 스스로 다시 받아 재시도할 수 있게 심어 둔다.
// 배포로 서버가 재시작되면 세션과 토큰이 같이 사라지는데, 그때 열려 있던 화면이
// 새로고침 전까지 아무것도 저장하지 못하는 상태가 된다
onCsrfRejected(refreshCsrfToken);
