import { getApiData } from '../../../shared/api/blogApiClient';
import { toPostBody, toPostToc } from '../../../shared/post/postContentMapper';
import {
  formatDate,
  formatNumber,
  getFallbackPostImageUrl,
  toPostCard,
} from '../../../shared/post/postCardMapper';

const DEFAULT_RELATED_POST_SIZE = 3;

export function getCategories() {
  return getApiData('/api/v1/blog/categories');
}

export async function getPostDetail(postId) {
  const post = await getApiData(`/api/v1/blog/posts/${postId}`);
  return toPostDetail(post);
}

export async function getAdjacentPosts(postId) {
  const adjacentPosts = await getApiData(`/api/v1/blog/posts/${postId}/adjacent`);

  return {
    prev: adjacentPosts?.previousPost ?? null,
    next: adjacentPosts?.nextPost ?? null,
  };
}

export async function getRelatedPosts(postId, size = DEFAULT_RELATED_POST_SIZE) {
  const searchParams = new URLSearchParams({
    size: String(size),
  });

  const posts = await getApiData(`/api/v1/blog/posts/${postId}/related?${searchParams.toString()}`);
  return Array.isArray(posts) ? posts.map(toPostCard) : null;
}

function toPostDetail(post) {
  const body = toPostBody(post.content);

  return {
    post: {
      id: post.id,
      categoryId: post.categoryId,
      category: post.categoryName,
      title: post.title,
      excerpt: post.excerpt,
      publishedAt: formatDate(post.publishedAt),
      views: formatNumber(post.views),
      coverImageUrl: post.thumbnailImageUrl || getFallbackPostImageUrl(),
      coverImageAlt: `${post.title} 대표 이미지`,
      tags: [],
      commentCount: 0,
    },
    body,
    toc: toPostToc(body),
  };
}
