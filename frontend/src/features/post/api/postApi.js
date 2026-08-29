import { getApiData } from '../../../shared/api/blogApiClient';
import { toPostBody, toPostToc } from '../../../shared/post/postContentMapper';
import {
  formatDate,
  formatNumber,
  getFallbackPostImageUrl,
  toPostCard,
} from '../../../shared/post/postCardMapper';

const DEFAULT_RELATED_POST_SIZE = 3;
const DEFAULT_COMMENT_PAGE_SIZE = 20;
const COMMENT_PLACEHOLDER = '따뜻한 댓글 하나가 다음 글을 쓰게 만듭니다.';
const COMMENT_MAX_LENGTH = 1000;

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

export async function getPostComments(
  postId,
  { cursor = null, size = DEFAULT_COMMENT_PAGE_SIZE } = {},
) {
  const searchParams = new URLSearchParams({
    size: String(size),
  });

  if (cursor !== null && cursor !== undefined) {
    searchParams.set('cursor', String(cursor));
  }

  const comments = await getApiData(`/api/v1/blog/posts/${postId}/comments?${searchParams.toString()}`);
  return toCommentPage(comments);
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

function toCommentPage(commentPage) {
  return {
    total: Number(commentPage?.total ?? 0),
    items: Array.isArray(commentPage?.items) ? commentPage.items.map(toCommentItem) : [],
    nextCursor: commentPage?.nextCursor ?? null,
    hasNext: Boolean(commentPage?.hasNext),
    placeholder: COMMENT_PLACEHOLDER,
    maxLength: COMMENT_MAX_LENGTH,
  };
}

function toCommentItem(comment) {
  const deleted = Boolean(comment.deleted);

  return {
    id: comment.id,
    author: deleted ? '' : getCommentNickname(comment),
    createdAt: formatDateTime(comment.createdAt),
    content: deleted ? '' : (comment.content ?? ''),
    isSecret: Boolean(comment.secret),
    deleted,
    hiddenReplyCount: 0,
    replies: Array.isArray(comment.replies) ? comment.replies.map(toCommentReply) : [],
  };
}

function toCommentReply(reply) {
  return {
    id: reply.id,
    author: getCommentNickname(reply),
    createdAt: formatDateTime(reply.createdAt),
    content: reply.content ?? '',
    isAuthor: Boolean(reply.authorComment),
    deleted: Boolean(reply.deleted),
  };
}

function getCommentNickname(comment) {
  return comment.nickname ?? comment.author ?? '';
}

function formatDateTime(value) {
  if (!value) {
    return '';
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return '';
  }

  const datePart = formatDate(value);
  const timePart = new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);

  return `${datePart} ${timePart}`;
}
