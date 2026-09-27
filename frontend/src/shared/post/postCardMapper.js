/**
 * 대표 이미지가 없는 글에 대신 쓰는 그림.
 *
 * <p>WebP 다. 같은 그림의 PNG 는 1,540KB 였는데 67KB 가 됐다 — 23배다. 사진을 PNG 로
 * 두면 원래 이만큼 커진다. PNG 는 무손실이라 스크린샷·로고·투명 이미지 자리다.</p>
 *
 * <p>대표 이미지가 없는 글은 <b>목록의 카드마다</b> 이 그림을 쓴다. 한 장이 무거우면
 * 그 무게가 화면 전체에 깔린다.</p>
 */
const FALLBACK_POST_IMAGE_URL = '/images/blog-hero-workspace.webp';

export function toPostCard(post) {
  return {
    id: post.id,
    categoryId: post.categoryId,
    category: post.categoryName,
    title: post.title,
    publishedAt: formatDate(post.publishedAt),
    views: formatNumber(post.views),
    imageUrl: post.thumbnailImageUrl || FALLBACK_POST_IMAGE_URL,
  };
}

export function formatDate(value) {
  if (!value) {
    return '';
  }

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
    .format(new Date(value))
    .replace(/\.$/, '');
}

export function formatNumber(value) {
  return Number(value ?? 0).toLocaleString('ko-KR');
}

export function getFallbackPostImageUrl() {
  return FALLBACK_POST_IMAGE_URL;
}
