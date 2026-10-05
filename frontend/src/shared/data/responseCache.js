/**
 * 화면이 받은 응답을 키별로 잠깐 들고 있는다.
 *
 * 뒤로 가기나 같은 글로 돌아올 때 스켈레톤부터 다시 그리지 않으려는 것이다. 들고 있는 값은
 * 먼저 보여 주기만 하고, 화면은 언제나 새로 받아 바꾼다 — 오래된 값이 그대로 남는 일은 없다.
 *
 * 앱을 새로 열면 비어 있다. 개수를 넘으면 가장 오래 안 쓴 것부터 버려 메모리가 계속 늘지 않게 한다.
 */
const MAX_ENTRIES = 50;
const entries = new Map();

export function readCachedResponse(key) {
  if (!entries.has(key)) {
    return undefined;
  }

  // 다시 넣어 맨 뒤로 보낸다. Map 은 넣은 순서를 지키므로 맨 앞이 가장 오래 안 쓴 것이다
  const value = entries.get(key);
  entries.delete(key);
  entries.set(key, value);

  return value;
}

export function writeCachedResponse(key, value) {
  entries.delete(key);
  entries.set(key, value);

  if (entries.size > MAX_ENTRIES) {
    entries.delete(entries.keys().next().value);
  }
}
