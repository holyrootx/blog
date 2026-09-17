/**
 * 서버가 내려준 에러를 그대로 들고 다니는 예외.
 * 화면에서 code 로 분기하거나 message 를 그대로 보여줄 수 있게 한다.
 */
export class ApiError extends Error {
  constructor(message, code, status) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

/**
 * 세션이 끊겼을 때 부를 함수. 로그인 화면으로 보내는 일은 관리자 쪽 코드가 정한다.
 *
 * 화면마다 401 을 처리하면 빠뜨리는 곳이 반드시 생긴다. 한 곳에서 받는다.
 */
let unauthorizedHandler = null;

export function onUnauthorized(handler) {
  unauthorizedHandler = handler;
}

/**
 * 로그인 실패(ADMIN_LOGIN_FAILED)는 같은 401 이지만 세션이 끊긴 것이 아니다.
 * 이걸 구분하지 않으면 비밀번호를 틀릴 때마다 로그인 화면으로 다시 튕긴다.
 */
function notifyIfSessionExpired(code) {
  if (code === 'UNAUTHORIZED') {
    unauthorizedHandler?.();
  }
}

export async function getApiData(path) {
  const response = await fetch(path, {
    headers: {
      Accept: 'application/json',
    },
  });

  // 실패 응답에도 본문이 있으므로 먼저 읽는다. 없을 수도 있어 실패는 삼킨다
  const body = await response.json().catch(() => null);

  // 서버가 준 code·message 를 버리지 않는다.
  // 버리면 화면은 "불러오지 못했습니다"만 알고 왜 실패했는지 알 수 없다
  if (!response.ok || body?.success === false) {
    notifyIfSessionExpired(body?.code);

    throw new ApiError(
      body?.message ?? `요청에 실패했습니다. (status=${response.status})`,
      body?.code,
      response.status,
    );
  }

  return body?.data ?? null;
}

/**
 * 상태를 바꾸는 요청에 붙일 CSRF 토큰.
 *
 * 값을 여기 두는 이유는 화면마다 들고 다니면 로그인 뒤 갱신을 빠뜨리는 곳이 생기기 때문이다.
 * 서버가 로그인·로그아웃 때 토큰을 새로 발급하므로 그 시점에 다시 받아야 한다.
 */
let csrf = null;

export function setCsrfToken(next) {
  csrf = next;
}

function csrfHeader() {
  return csrf?.headerName ? { [csrf.headerName]: csrf.token } : {};
}

/**
 * 파일을 올리는 요청.
 *
 * Content-Type 을 직접 넣지 않는다. FormData 를 보낼 때는 브라우저가 경계 문자열까지 붙여
 * 헤더를 만드는데, 우리가 먼저 적으면 그 경계가 빠져 서버가 본문을 못 읽는다.
 */
export async function sendApiFile(path, formData) {
  const response = await fetch(path, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      ...csrfHeader(),
    },
    body: formData,
  });

  const payload = await response.json().catch(() => null);

  if (!response.ok || payload?.success === false) {
    notifyIfSessionExpired(payload?.code);

    throw new ApiError(
      payload?.message ?? `업로드에 실패했습니다. (status=${response.status})`,
      payload?.code,
      response.status,
    );
  }

  return payload?.data ?? null;
}

/**
 * 값을 바꾸는 요청(POST·PUT·DELETE).
 * 실패하면 서버가 준 메시지를 그대로 던진다 — 화면에서 왜 실패했는지 보여줘야 하기 때문.
 */
export async function sendApiData(path, { method, body } = {}) {
  const response = await fetch(path, {
    method,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...csrfHeader(),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  // 에러 응답도 본문이 있으므로 먼저 읽는다. 본문이 없을 수도 있어 실패는 삼킨다
  const payload = await response.json().catch(() => null);

  if (!response.ok || payload?.success === false) {
    notifyIfSessionExpired(payload?.code);

    throw new ApiError(
      payload?.message ?? `요청에 실패했습니다. (status=${response.status})`,
      payload?.code,
      response.status,
    );
  }

  return payload?.data ?? null;
}
