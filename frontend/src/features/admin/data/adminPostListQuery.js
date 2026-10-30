const PAGE_SIZES = [10, 20, 50, 100];
const STATUSES = ['PUBLISHED', 'SCHEDULED', 'PRIVATE', 'DRAFT', 'TRASH'];

function positiveInteger(value, fallback, maximum = Number.MAX_SAFE_INTEGER) {
	if (typeof value !== 'string' || !/^\d+$/.test(value)) {
		return fallback;
	}
	const number = Number(value);
	return Number.isSafeInteger(number) && number > 0 && number <= maximum ? number : fallback;
}

/** 목록과 편집 화면이 같은 규칙으로 복귀 조건을 읽는다. 배열이나 잘못된 값은 기본값으로 돌린다. */
export function readAdminPostListQuery(query = {}) {
	const size = positiveInteger(query.size, 20);
	const categoryId = positiveInteger(query.categoryId, null);
	return {
		page: positiveInteger(query.page, 1, 2_147_483_647),
		pageSize: PAGE_SIZES.includes(size) ? size : 20,
		status: STATUSES.includes(query.status) ? query.status : '',
		categoryId: categoryId === null ? '' : String(categoryId),
		keyword: typeof query.keyword === 'string' ? query.keyword.trim() : '',
	};
}

/** 기본값은 주소에서 생략한다. 입력 중인 조건 대신 마지막 조회에 적용한 조건을 넘겨야 한다. */
export function toAdminPostListQuery(state) {
	const normalized = readAdminPostListQuery({
		page: String(state.page), size: String(state.pageSize), status: state.status,
		categoryId: String(state.categoryId), keyword: state.keyword,
	});
	return {
		...(normalized.page !== 1 ? { page: String(normalized.page) } : {}),
		...(normalized.pageSize !== 20 ? { size: String(normalized.pageSize) } : {}),
		...(normalized.status ? { status: normalized.status } : {}),
		...(normalized.categoryId ? { categoryId: normalized.categoryId } : {}),
		...(normalized.keyword ? { keyword: normalized.keyword } : {}),
	};
}
