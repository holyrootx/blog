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
    throw new ApiError(
      body?.message ?? `요청에 실패했습니다. (status=${response.status})`,
      body?.code,
      response.status,
    );
  }

  return body?.data ?? null;
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
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  // 에러 응답도 본문이 있으므로 먼저 읽는다. 본문이 없을 수도 있어 실패는 삼킨다
  const payload = await response.json().catch(() => null);

  if (!response.ok || payload?.success === false) {
    throw new ApiError(
      payload?.message ?? `요청에 실패했습니다. (status=${response.status})`,
      payload?.code,
      response.status,
    );
  }

  return payload?.data ?? null;
}
