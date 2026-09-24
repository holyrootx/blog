import { getApiData, sendApiData } from '../../../shared/api/blogApiClient';
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

/**
 * 공개 글 목록 한 페이지.
 *
 * 화면은 1부터 세고 서버는 0부터 센다. 그 변환을 여기서 한 번만 한다 —
 * 화면 곳곳에서 빼고 더하면 언젠가 한 곳을 빠뜨린다.
 */
export async function getPosts({
  page = 1,
  size = 12,
  categoryId = null,
  sort = 'latest',
  keyword = '',
} = {}) {
  const params = new URLSearchParams({
    page: String(Math.max(0, page - 1)),
    size: String(size),
    sort,
  });

  if (categoryId) {
    params.set('categoryId', String(categoryId));
  }

  if (keyword) {
    params.set('q', keyword);
  }

  const result = await getApiData(`/api/v1/blog/posts?${params.toString()}`);

  return {
    items: Array.isArray(result?.items) ? result.items.map(toPostCard) : [],
    page: (result?.page ?? 0) + 1,
    size: result?.size ?? size,
    totalElements: result?.totalElements ?? 0,
    totalPages: result?.totalPages ?? 0,
  };
}

/**
 * 검색창 아래에 바로 띄울 글 몇 건.
 *
 * 목록과 다른 자리를 쓰는 이유는 타자마다 불리기 때문이다. 개수 계산이 없고, 서버도
 * 여기서는 검색 기록을 남기지 않는다 — 덜 친 말까지 쌓이면 나중에 쓸 수 없는 기록이 된다.
 */
export async function getPostSuggestions(keyword, { signal } = {}) {
  const params = new URLSearchParams({ q: keyword });

  const posts = await getApiData(`/api/v1/blog/posts/suggest?${params.toString()}`, { signal });

  return Array.isArray(posts) ? posts : [];
}

export async function getPostDetail(postId) {
  const post = await getApiData(`/api/v1/blog/posts/${postId}`);
  return toPostDetail(post);
}

export function setPostReaction(postId, type) {
  return sendApiData(`/api/v1/blog/posts/${postId}/reaction`, {
    method: 'PUT',
    body: { type },
  });
}

export function removePostReaction(postId, type) {
  const searchParams = new URLSearchParams({ type });

  return sendApiData(`/api/v1/blog/posts/${postId}/reaction?${searchParams.toString()}`, {
    method: 'DELETE',
  });
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

export function createPostComment(postId, { content, parentId = null }) {
  return sendApiData(`/api/v1/blog/posts/${postId}/comments`, {
    method: 'POST',
    body: { content, parentId },
  });
}

export function setCommentReaction(commentId, type) {
  return sendApiData(`/api/v1/blog/comments/${commentId}/reaction`, {
    method: 'PUT',
    body: { type },
  });
}

export function removeCommentReaction(commentId, type) {
  const searchParams = new URLSearchParams({ type });

  return sendApiData(`/api/v1/blog/comments/${commentId}/reaction?${searchParams.toString()}`, {
    method: 'DELETE',
  });
}

/** 내 댓글 고치기. 남의 댓글이면 서버가 COMMENT_NOT_MINE 으로 거절한다 */
export function updatePostComment(commentId, content) {
  return sendApiData(`/api/v1/blog/comments/${commentId}`, {
    method: 'PUT',
    body: { content },
  });
}

/**
 * 내 댓글 지우기.
 *
 * 글이 사라지지 않고 "삭제된 댓글입니다" 로 바뀐다. 답글이 달려 있으면 그 답글은
 * 그대로 남아야 해서, 서버가 표시만 바꾼다.
 */
export function deletePostComment(commentId) {
  return sendApiData(`/api/v1/blog/comments/${commentId}`, {
    method: 'DELETE',
  });
}

/**
 * 댓글 신고.
 *
 * 사유는 서버의 CommentReportReason 과 같은 말을 쓴다. detail 은 기타일 때만 채운다.
 */
export function reportComment(commentId, { reason, detail = null }) {
  return sendApiData(`/api/v1/blog/comments/${commentId}/reports`, {
    method: 'POST',
    body: { reason, detail },
  });
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
      likeCount: Number(post.likeCount ?? 0),
      dislikeCount: Number(post.dislikeCount ?? 0),
      likedByMe: Boolean(post.likedByMe),
      dislikedByMe: Boolean(post.dislikedByMe),
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
    isAuthor: Boolean(comment.authorComment),
    deleted,
    likeCount: Number(comment.likeCount ?? 0),
    dislikeCount: Number(comment.dislikeCount ?? 0),
    likedByMe: Boolean(comment.likedByMe),
    dislikedByMe: Boolean(comment.dislikedByMe),
    mine: Boolean(comment.mine),
    edited: Boolean(comment.edited),
    hiddenByAdmin: Boolean(comment.hiddenByAdmin),
    reportedByMe: Boolean(comment.reportedByMe),
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
    likeCount: Number(reply.likeCount ?? 0),
    dislikeCount: Number(reply.dislikeCount ?? 0),
    likedByMe: Boolean(reply.likedByMe),
    dislikedByMe: Boolean(reply.dislikedByMe),
    mine: Boolean(reply.mine),
    edited: Boolean(reply.edited),
    reportedByMe: Boolean(reply.reportedByMe),
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
