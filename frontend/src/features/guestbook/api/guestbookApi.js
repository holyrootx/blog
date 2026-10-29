import { getApiData, sendApiData } from '../../../shared/api/blogApiClient';
import { formatSeoulDateTime } from '../../../shared/time/seoulTime';

const GUESTBOOK_API = '/api/v1/blog/guestbook';
const ADMIN_GUESTBOOK_API = '/api/v1/admin/blog/guestbook';

/** 서버의 GuestbookEntry.MAX_CONTENT_LENGTH 와 같은 값. 응답에 실려 오면 그 값을 쓴다 */
export const GUESTBOOK_MAX_LENGTH = 500;
export const GUESTBOOK_PAGE_SIZE = 10;

/**
 * 화면은 1쪽부터, 서버는 0쪽부터 센다. 여기서 바꾼다.
 * entryId 를 주면 서버가 그 글이 있는 쪽을 골라 준다. 돌아온 page 가 실제로 받은 쪽이다.
 */
export async function getGuestbook({ page = 1, size = GUESTBOOK_PAGE_SIZE, entryId = null } = {}, { signal } = {}) {
	const query = new URLSearchParams({ page: String(Math.max(page, 1) - 1), size: String(size) });
	if (entryId !== null) {
		query.set('entry', String(entryId));
	}
	const result = await getApiData(`${GUESTBOOK_API}?${query.toString()}`, { signal });

	return {
		items: Array.isArray(result?.items) ? result.items.map(toGuestbookEntry) : [],
		page: Number(result?.page ?? 0) + 1,
		totalElements: Number(result?.totalElements ?? 0),
		totalPages: Number(result?.totalPages ?? 0),
		maxLength: Number(result?.maxLength ?? GUESTBOOK_MAX_LENGTH),
	};
}

export function createGuestbookEntry({ content, secret }) {
	return sendApiData(GUESTBOOK_API, { method: 'POST', body: { content, secret } });
}

export function updateGuestbookEntry(entryId, { content, secret }) {
	return sendApiData(`${GUESTBOOK_API}/${entryId}`, { method: 'PUT', body: { content, secret } });
}

export function deleteGuestbookEntry(entryId) {
	return sendApiData(`${GUESTBOOK_API}/${entryId}`, { method: 'DELETE' });
}

/** 사유는 댓글 신고와 같은 목록(CommentReportReason)을 쓴다 */
export function reportGuestbookEntry(entryId, { reason, detail = null }) {
	return sendApiData(`${GUESTBOOK_API}/${entryId}/reports`, { method: 'POST', body: { reason, detail } });
}

/**
 * 운영자 답글. 관리자 API 라서 서버가 운영자인지 다시 본다 — 화면의 운영자 표시는 편의일 뿐이다.
 * 글마다 하나라 달기와 고치기가 같은 요청이다.
 */
export function saveGuestbookReply(entryId, content) {
	return sendApiData(`${ADMIN_GUESTBOOK_API}/${entryId}/reply`, { method: 'PUT', body: { content } });
}

export function deleteGuestbookReply(entryId) {
	return sendApiData(`${ADMIN_GUESTBOOK_API}/${entryId}/reply`, { method: 'DELETE' });
}

function toGuestbookEntry(entry) {
	return {
		id: entry.id,
		author: entry.nickname ?? '',
		writtenByAdmin: Boolean(entry.writtenByAdmin),
		content: entry.content ?? '',
		secret: Boolean(entry.secret),
		readable: Boolean(entry.readable),
		mine: Boolean(entry.mine),
		edited: Boolean(entry.edited),
		hiddenByAdmin: Boolean(entry.hiddenByAdmin),
		reportedByMe: Boolean(entry.reportedByMe),
		// CommentActions 는 지운 글에 단추를 안 그린다. 목록에 오는 지운 글은 운영자가 가린 내 글뿐이고,
		// 서버가 고치기·지우기를 거절하므로 단추도 감춘다
		deleted: Boolean(entry.hiddenByAdmin),
		createdAt: formatSeoulDateTime(entry.createdAt),
		createdAtIso: entry.createdAt ?? '',
		reply: entry.reply ? {
			author: entry.reply.nickname ?? '',
			content: entry.reply.content ?? '',
			repliedAt: formatSeoulDateTime(entry.reply.repliedAt),
			repliedAtIso: entry.reply.repliedAt ?? '',
		} : null,
	};
}
