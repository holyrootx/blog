/**
 * 해시가 가리키는 자리로 보낸다.
 *
 * <p>Vue Router 는 {@code scrollBehavior} 를 주지 않으면 해시를 무시한다. 알림을 눌러
 * {@code #comment-12} 로 보내도 주소만 바뀌고 화면은 그대로였다.</p>
 *
 * <p>댓글은 글 본문보다 늦게 도착한다. 다른 글에서 알림을 누르면 이동하는 순간에는
 * 그 댓글이 아직 화면에 없어서, 바로 찾으면 못 찾고 맨 위에 머문다.
 * 그래서 잠깐 기다렸다가 찾는다.</p>
 */

/** 스티키 헤더에 가리지 않을 만큼. 본문 제목의 scroll-margin-top 과 같은 값이다 */
export const SCROLL_OFFSET = 88;

/** 댓글이 늦어도 이 정도면 온다. 더 기다려도 못 찾으면 맨 위로 둔다 */
const WAIT_LIMIT_MS = 2000;

export function scrollToHash(to, from, savedPosition) {
  // 뒤로 가기는 보던 자리로 돌려놓는 것이 맞다
  if (savedPosition) {
    return savedPosition;
  }

  if (!to.hash) {
    return { top: 0 };
  }

  return waitForElement(to.hash).then((found) => {
    if (!found) {
      return { top: 0 };
    }

    return { el: to.hash, top: SCROLL_OFFSET, behavior: 'smooth' };
  });
}

function waitForElement(selector) {
  return new Promise((resolve) => {
    const deadline = Date.now() + WAIT_LIMIT_MS;

    const look = () => {
      if (document.querySelector(selector)) {
        resolve(true);
        return;
      }

      if (Date.now() > deadline) {
        resolve(false);
        return;
      }

      window.requestAnimationFrame(look);
    };

    look();
  });
}
