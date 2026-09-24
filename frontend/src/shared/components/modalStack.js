// 열려 있는 모달 스택.
//
// 이 배열은 반드시 모듈 수준에 있어야 한다.
// <script setup> 안의 최상위 코드는 setup() 본문으로 컴파일되어 인스턴스마다 실행되므로,
// 거기에 배열을 두면 모달마다 각자 하나짜리 스택을 갖게 되어 겹침을 판단할 수 없다.
const openModals = [];

export function pushModal(id) {
  if (!openModals.includes(id)) {
    openModals.push(id);
  }
}

export function popModal(id) {
  const index = openModals.indexOf(id);

  if (index !== -1) {
    openModals.splice(index, 1);
  }
}

/** 지금 맨 위에 있는 모달인지. Esc 는 맨 위 것만 닫는다 */
export function isTopModal(id) {
  return openModals[openModals.length - 1] === id;
}

/** 열린 모달이 하나도 없는지. 배경 스크롤 잠금을 풀어도 되는 시점 */
export function hasNoOpenModal() {
  return openModals.length === 0;
}
