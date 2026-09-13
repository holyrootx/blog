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

  if (!response.ok) {
    throw new Error(`API request failed. status=${response.status}, path=${path}`);
  }

  const body = await response.json();

  if (!body.success) {
    throw new Error(`API response failed. code=${body.code}, path=${path}`);
  }

  return body.data;
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
