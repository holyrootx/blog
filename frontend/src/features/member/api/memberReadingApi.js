import { getApiData, sendApiData } from '../../../shared/api/blogApiClient';
import { getFallbackPostImageUrl } from '../../../shared/post/postCardMapper';

const MEMBER_API_BASE = '/api/v1/auth/me';

/**
 * 내가 최근 본 글 한 페이지. 화면은 1쪽부터, 서버는 0쪽부터 센다.
 */
export async function getRecentPosts({ page = 1, size = 10 } = {}) {
	const params = new URLSearchParams({ page: String(Math.max(0, page - 1)), size: String(size) });
	const result = await getApiData(`${MEMBER_API_BASE}/recent-posts?${params.toString()}`);

	return {
		items: Array.isArray(result?.items) ? result.items.map(toRecentPost) : [],
		page: Number(result?.page ?? 0) + 1,
		totalElements: Number(result?.totalElements ?? 0),
		totalPages: Number(result?.totalPages ?? 0),
	};
}

/**
 * 카드 여러 장의 "최근 본 글" 여부를 한 번에 묻는다. 본 적 있는 글만 돌아온다.
 */
export async function getViewedPosts(postIds) {
	const params = new URLSearchParams({ postIds: postIds.join(',') });
	const result = await getApiData(`${MEMBER_API_BASE}/viewed-posts?${params.toString()}`);

	return Array.isArray(result)
		? result.map((item) => ({ postId: item.postId, lastViewedAt: item.lastViewedAt ?? null }))
		: [];
}

/** 이 글에서 읽던 자리. 저장한 적이 없으면 null */
export async function getReadingPosition(postId) {
	const position = await getApiData(`${MEMBER_API_BASE}/reading-positions/${postId}`);

	if (!position) {
		return null;
	}

	return {
		blockIndex: Number(position.blockIndex),
		blockOffset: Number(position.blockOffset),
		blockHash: position.blockHash,
		savedAt: position.savedAt ?? null,
	};
}

export function saveReadingPosition(postId, { blockIndex, blockOffset, blockHash }, { keepalive = false } = {}) {
	return sendApiData(`${MEMBER_API_BASE}/reading-positions/${postId}`, {
		method: 'PUT',
		body: { blockIndex, blockOffset, blockHash },
		keepalive,
	});
}

function toRecentPost(post) {
	return {
		id: post.postId,
		title: post.title ?? '',
		category: post.categoryName ?? '',
		imageUrl: post.thumbnailImageUrl || getFallbackPostImageUrl(),
		lastViewedAt: post.lastViewedAt ?? null,
	};
}
