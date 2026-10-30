import { reactive } from 'vue';

import { getViewedPosts } from '../api/memberReadingApi';

/**
 * 글 카드의 "최근 본 글" 표시.
 *
 * <p>카드는 한 화면에 여러 묶음(최신 글, 인기 글, 관련 글)으로 흩어져 있다. 카드마다 묻거나 묶음마다
 * 물으면 요청이 화면에 깔리는 카드 수만큼 나간다. 그래서 같은 순간에 그려진 카드의 글 번호를 모아
 * 한 번에 묻는다.</p>
 *
 * <p>답은 누구의 기록인지와 함께 들고 있는다. 로그아웃하거나 다른 계정으로 바뀌면 남의 표시가
 * 남지 않게 비운다.</p>
 */
const MAX_IDS_PER_REQUEST = 50;

// 글 번호 → 마지막으로 본 시각. 물어봤는데 본 적 없는 글은 null 로 둬서 다시 묻지 않는다
const viewedAt = reactive(new Map());
const pending = new Set();
let ownerId = null;
let ownerGeneration = 0;
let flushQueued = false;

export function viewedAtOf(postId) {
	return viewedAt.get(Number(postId)) ?? null;
}

/** 이 회원이 이 글들을 봤는지 묻는다. 같은 순간에 들어온 요청은 모아서 보낸다 */
export function requestViewedPosts(memberId, postIds) {
	if (!memberId) {
		resetOwner(null);
		return;
	}
	if (memberId !== ownerId) {
		resetOwner(memberId);
	}

	postIds
		.map(Number)
		.filter((postId) => Number.isInteger(postId) && postId > 0 && !viewedAt.has(postId))
		.forEach((postId) => pending.add(postId));

	if (pending.size > 0 && !flushQueued) {
		flushQueued = true;
		// 같은 렌더에서 마운트되는 카드들이 다 등록한 뒤에 보낸다
		queueMicrotask(flush);
	}
}

/** 방금 연 글. 카드 목록으로 돌아갔을 때 다시 묻지 않아도 표시되게 한다 */
export function markPostViewed(memberId, postId, at = new Date().toISOString()) {
	if (!memberId) {
		return;
	}
	if (memberId !== ownerId) {
		resetOwner(memberId);
	}
	viewedAt.set(Number(postId), at);
}

async function flush() {
	flushQueued = false;
	const generation = ownerGeneration;
	const ids = [...pending];
	pending.clear();

	for (let start = 0; start < ids.length; start += MAX_IDS_PER_REQUEST) {
		if (generation !== ownerGeneration) {
			return;
		}
		const chunk = ids.slice(start, start + MAX_IDS_PER_REQUEST);
		try {
			// 묶음은 보통 하나뿐이고, 응답을 기다려 순서대로 보내야 서버에 한꺼번에 몰리지 않는다
			const viewed = await getViewedPosts(chunk);
			if (generation !== ownerGeneration) {
				return;
			}
			const found = new Map(viewed.map((item) => [Number(item.postId), item.lastViewedAt]));
			chunk.forEach((postId) => {
				// 조회를 기다리는 동안 직접 읽은 글의 표시와 시각은 이전 응답으로 덮지 않는다.
				if (!viewedAt.has(postId)) {
					viewedAt.set(postId, found.get(postId) ?? null);
				}
			});
		} catch (error) {
			// 표시는 덤이라 실패해도 카드는 그대로 보인다. 다음에 그 카드가 다시 그려질 때 또 묻는다
			console.warn(error);
		}
	}
}

function resetOwner(memberId) {
	ownerGeneration += 1;
	ownerId = memberId;
	viewedAt.clear();
	pending.clear();
}
