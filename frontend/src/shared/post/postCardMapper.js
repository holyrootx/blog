const FALLBACK_POST_IMAGE_URL = '/images/blog-hero-workspace.png';

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
