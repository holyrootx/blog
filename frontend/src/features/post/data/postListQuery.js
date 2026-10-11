const MAX_PAGE = 2147483647;
const MIN_KEYWORD_LENGTH = 2;

// 목록 조회와 대표 주소가 같은 조건을 사용해야 다른 페이지를 하나로 합치지 않는다.
export function readPostListQuery(query = {}) {
  const typed = typeof query.q === 'string' ? query.q.trim() : '';

  return {
    page: positiveInteger(query.page, MAX_PAGE) ?? 1,
    categoryId: positiveInteger(query.category),
    sort: query.sort === 'popular' ? 'popular' : 'latest',
    keyword: typed.length >= MIN_KEYWORD_LENGTH ? typed : '',
  };
}

export function toPostListQuery({ page, categoryId, sort, keyword }) {
  const query = {};
  if (page > 1) query.page = String(page);
  if (categoryId) query.category = String(categoryId);
  if (sort === 'popular') query.sort = sort;
  if (keyword) query.q = keyword;
  return query;
}

function positiveInteger(value, max = Number.MAX_SAFE_INTEGER) {
  if (typeof value !== 'string' || !/^\d+$/.test(value)) return null;
  const number = Number(value);
  return Number.isSafeInteger(number) && number > 0 && number <= max ? number : null;
}
