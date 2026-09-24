import { getCategories } from '../api/postApi';

/**
 * 분류 목록.
 *
 * <p>관리자가 카테고리 관리에서 넣은 값을 서버에서 받아 온다. 화면에 박아 두지 않는다 —
 * 분류를 하나 늘릴 때마다 코드를 고쳐 배포해야 하는 꼴이 되고, 그러다 보면 관리자 화면에
 * 넣은 것과 화면에 보이는 것이 어긋난다.</p>
 *
 * <p>요청은 한 번만 나간다. 헤더와 목록 화면이 같은 값을 쓰는데, 각자 부르면 한 화면을
 * 여는 데 같은 요청이 두 번 나간다.</p>
 */
let request = null;

export function loadCategories() {
  if (!request) {
    request = getCategories()
      .then((found) => (Array.isArray(found) ? found : []))
      // 분류를 못 받아도 글은 읽을 수 있어야 한다. 분류 줄만 빠진다
      .catch(() => []);
  }

  return request;
}
