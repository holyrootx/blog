import { readPostListQuery, toPostListQuery } from '../../features/post/data/postListQuery.js';

export function routeDocumentMeta(route) {
	const meta = {
		title: route.meta.title,
		path: route.path.replace(/\/+$/, '') || '/',
		robots: route.meta.robots,
	};

	if (route.name !== 'post-list') {
		return meta;
	}

	const condition = readPostListQuery(route.query);
	const query = new URLSearchParams(toPostListQuery(condition)).toString();
	meta.path = `/posts${query ? `?${query}` : ''}`;

	// 페이지·카테고리는 별도 주소로 남기고, 임의 검색 결과는 색인에서 제외한다.
	if (condition.keyword) {
		meta.title = `‘${condition.keyword}’ 검색 결과`;
		meta.robots = 'noindex,follow';
	}

	return meta;
}
