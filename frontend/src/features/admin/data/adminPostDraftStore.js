const KEY_PREFIX = 'admin:post-draft:';

// 7일 지난 스냅샷은 앱 시작 시 정리한다
const MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;

/**
 * 글 편집 중 내용을 브라우저에 남긴다. 서버 자동저장이 아니다 —
 * 서버에 자동저장하면 버전 관리 문제를 끌고 온다.
 *
 * 대상별로 키를 나눈다. 한 키에 덮어쓰면 글 A를 두고 B를 열었다 돌아왔을 때
 * A의 스냅샷이 B로 덮여 사라진다.
 */
export function savePostDraft(key, form, baseUpdatedAt = null) {
  write(KEY_PREFIX + key, {
    form,
    savedAt: new Date().toISOString(),
    // 이 스냅샷이 어느 서버 값을 기준으로 만들어졌는지
    baseUpdatedAt,
  });
}

export function loadPostDraft(key) {
  const draft = read(KEY_PREFIX + key);

  if (!draft || !draft.form) {
    return null;
  }

  return draft;
}

export function clearPostDraft(key) {
  try {
    localStorage.removeItem(KEY_PREFIX + key);
  } catch (error) {
    // 저장소를 막아둔 브라우저에서도 화면은 돌아가야 한다
    console.warn(error);
  }
}

/** 오래된 스냅샷 정리. 앱 시작 시 한 번 부른다 */
export function cleanUpPostDrafts() {
  try {
    const now = Date.now();

    Object.keys(localStorage)
      .filter((key) => key.startsWith(KEY_PREFIX))
      .forEach((key) => {
        const draft = read(key);
        const savedAt = draft?.savedAt ? new Date(draft.savedAt).getTime() : 0;

        if (!savedAt || now - savedAt > MAX_AGE_MS) {
          localStorage.removeItem(key);
        }
      });
  } catch (error) {
    console.warn(error);
  }
}

function write(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch (error) {
    // 용량 초과나 사생활 보호 모드. 보관에 실패해도 글쓰기는 계속돼야 한다
    console.warn(error);
  }
}

function read(key) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : null;
  } catch (error) {
    console.warn(error);
    return null;
  }
}
