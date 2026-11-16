import { getCategories } from '../api/postApi';

/**
 * 분류 목록.
 *
 * <p>관리자가 카테고리 관리에서 넣은 값을 서버에서 받아 온다. 화면에 박아 두지 않는다 —
 * 분류를 하나 늘릴 때마다 코드를 고쳐 배포해야 하는 꼴이 되고, 그러다 보면 관리자 화면에
 * 넣은 것과 화면에 보이는 것이 어긋난다.</p>
 *
 * <p>진행 중인 요청은 함께 기다리고 성공한 결과는 재사용한다. 헤더와 목록 화면이 같은
 * 값을 쓰므로 같은 요청을 두 번 보내지 않는다. 실패했다면 다음 호출에서 다시 받는다.</p>
 */
let request = null;

export function loadCategories() {
	if (!request) {
		request = getCategories()
			.then((found) => (Array.isArray(found) ? found : []))
			.catch(() => {
				// 글 읽기는 유지하되 실패를 빈 분류로 캐시하면 다음에 메뉴를 열어도 복구되지 않는다.
				request = null;
				return [];
			});
	}

	return request;
}
