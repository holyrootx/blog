/**
 * 로그인하러 떠나기 전에 보던 자리를 기억한다.
 *
 * <p>소셜 로그인은 제공자 화면까지 브라우저가 통째로 다녀오는 길이라 라우터 상태가 남지
 * 않는다. 기억해 두지 않으면 글을 읽다 로그인한 사람이 홈으로 떨어지고, 쓰려던 댓글 자리를
 * 다시 찾아가야 한다.</p>
 *
 * <p>같은 출처의 sessionStorage 라 제공자에 다녀와도 남아 있고, 탭을 닫으면 사라진다.
 * 서버에 보관할 값이 아니다 — 돌아갈 화면일 뿐 권한과 무관하다.</p>
 */
const STORAGE_KEY = 'blog.member.returnPath';

export function rememberReturnPath(path) {
  // 외부 주소로 보내는 통로가 되지 않게 우리 경로만 받는다.
  // "//evil.com" 은 브라우저가 다른 출처로 읽으므로 같이 막는다
  if (typeof path !== 'string' || !path.startsWith('/') || path.startsWith('//')) {
    return;
  }

  try {
    window.sessionStorage.setItem(STORAGE_KEY, path);
  } catch {
    // 시크릿 모드나 저장 차단 환경이면 못 적는다. 홈으로 가는 것뿐이라 그냥 넘어간다
  }
}

/** 한 번 쓰고 지운다. 남겨 두면 다음 로그인에도 엉뚱한 곳으로 간다 */
export function takeReturnPath() {
  try {
    const path = window.sessionStorage.getItem(STORAGE_KEY);
    window.sessionStorage.removeItem(STORAGE_KEY);

    return path && path.startsWith('/') && !path.startsWith('//') ? path : null;
  } catch {
    return null;
  }
}
