const SITE_NAME = '정성주의 기록';
const DEFAULT_DESCRIPTION = '코드와 장비, 일과 생활 사이에서 배운 것을 적어 둡니다.';
const DEFAULT_IMAGE = '/images/blog-hero-workspace.webp';

/**
 * 문서 제목과 공유용 메타 태그.
 *
 * 전에는 어느 화면을 열어도 제목이 "정성주의 기록" 하나였다. 라우트에 title 을
 * 적어 두기는 했는데 읽는 곳이 없었다.
 *
 * 글 주소로 처음 들어올 때는 서버가 HTML에 메타 정보를 넣는다.
 * 이 함수는 앱 안에서 다른 화면으로 이동했을 때 같은 정보를 갱신한다.
 */
export function applyDocumentMeta({
  title,
  description = DEFAULT_DESCRIPTION,
  image = DEFAULT_IMAGE,
  path,
  type = 'website',
  robots = 'index,follow',
} = {}) {
  const fullTitle = title ? `${title} · ${SITE_NAME}` : SITE_NAME;
  const url = absolute(path ?? location.pathname);

  document.title = fullTitle;

  meta('name', 'description', description);
  meta('name', 'robots', robots);

  meta('property', 'og:type', type);
  meta('property', 'og:site_name', SITE_NAME);
  meta('property', 'og:title', title ?? SITE_NAME);
  meta('property', 'og:description', description);
  meta('property', 'og:url', url);
  meta('property', 'og:image', absolute(image || DEFAULT_IMAGE));

  // 카드 종류를 안 주면 트위터는 제목만 있는 작은 카드로 그린다
  meta('name', 'twitter:card', 'summary_large_image');
  meta('name', 'twitter:title', title ?? SITE_NAME);
  meta('name', 'twitter:description', description);
  meta('name', 'twitter:image', absolute(image || DEFAULT_IMAGE));

  canonical(robots.startsWith('noindex') ? null : url);
}

/**
 * 본문 블록에서 공유용 설명을 뽑는다.
 *
 * 요약을 안 적은 글이 있어서 그럴 때 쓴다. 문단과 인용만 본다 — 코드나 이미지는
 * 설명으로 읽히지 않는다.
 */
export function summarize(body, limit = 160) {
  const plain = (body ?? [])
    .filter((block) => block.type === 'paragraph' || block.type === 'quote')
    .map((block) => (block.inline ?? []).map((token) => token.text ?? '').join(''))
    .join(' ')
    .replace(/\s+/g, ' ')
    .trim();

  if (!plain) {
    return DEFAULT_DESCRIPTION;
  }

  return plain.length > limit ? `${plain.slice(0, limit).trimEnd()}…` : plain;
}

function meta(keyName, keyValue, content) {
  if (!content) {
    return;
  }

  let tag = document.head.querySelector(`meta[${keyName}="${keyValue}"]`);

  if (!tag) {
    tag = document.createElement('meta');
    tag.setAttribute(keyName, keyValue);
    document.head.appendChild(tag);
  }

  tag.setAttribute('content', content);
}

function canonical(url) {
  let tag = document.head.querySelector('link[rel="canonical"]');

  if (!url) {
    tag?.remove();
    return;
  }

  if (!tag) {
    tag = document.createElement('link');
    tag.setAttribute('rel', 'canonical');
    document.head.appendChild(tag);
  }

  tag.setAttribute('href', url);
}

function absolute(value) {
  if (!value) {
    return location.origin;
  }

  return /^https?:\/\//.test(value) ? value : new URL(value, location.origin).href;
}
