const KEY_PREFIX = 'admin:post-draft:';

/**
 * 임시저장 글이 쓰고 있는 이미지 주소.
 *
 * 임시저장은 브라우저 localStorage 에만 있어서 서버가 모른다. 그래서 서버 목록에는
 * "안 쓰임" 으로 내려오는데, 실제로는 작성 중인 글에서 쓰고 있을 수 있다.
 *
 * 이건 이 브라우저 것만 안다. 다른 기기에서 쓰던 글은 여기 안 잡힌다. 그래서 완전한
 * 보호 장치가 아니고, 서버의 7일 유예가 주 장치다.
 */
const IMAGE_URL = /!\[[^\]]*\]\(([^)\s]+)[^)]*\)|<img[^>]+src=["']([^"']+)["']/g;

/** @returns 주소 → 그 주소를 쓰는 임시저장 글 제목 목록 */
export function collectDraftImageUrls() {
  const found = new Map();

  let keys = [];

  try {
    keys = Object.keys(localStorage).filter((key) => key.startsWith(KEY_PREFIX));
  } catch (error) {
    // 저장소를 막아 둔 브라우저. 대조를 못 할 뿐 화면은 돌아가야 한다
    console.warn(error);
    return found;
  }

  keys.forEach((key) => {
    const draft = read(key);
    const form = draft?.form;

    if (!form) {
      return;
    }

    const title = form.title?.trim() || '제목 없는 글';

    urlsIn(form.content).forEach((url) => add(found, url, title));

    if (form.thumbnailImageUrl) {
      add(found, form.thumbnailImageUrl.trim(), title);
    }
  });

  return found;
}

function urlsIn(content) {
  if (!content) {
    return [];
  }

  const urls = [];

  for (const match of content.matchAll(IMAGE_URL)) {
    const url = match[1] ?? match[2];

    if (url) {
      urls.push(url.trim());
    }
  }

  return urls;
}

function add(found, url, title) {
  const titles = found.get(url) ?? [];

  if (!titles.includes(title)) {
    titles.push(title);
  }

  found.set(url, titles);
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
