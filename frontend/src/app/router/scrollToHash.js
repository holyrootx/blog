import { cancelCommentTargetNavigation, requestCommentTargetNavigation } from './commentTargetNavigation';

// 댓글 해시는 대상 조회와 렌더 완료를 기다리고, 본문 해시는 아래의 DOM 대기를 쓴다.
/** 스티키 헤더에 가리지 않을 만큼. 본문 제목의 scroll-margin-top 과 같은 값이다 */
export const SCROLL_OFFSET = 88;

/** 본문 제목 등 일반 해시의 렌더 대기 한도. 댓글 조회에는 적용하지 않는다. */
const WAIT_LIMIT_MS = 2000;

export function scrollToHash(to, from, savedPosition) {
  cancelCommentTargetNavigation();
  const comment = to.name === 'post-detail' && /^[1-9]\d*$/.test(String(to.params.id))
    && /^#comment-([1-9]\d*)$/.exec(to.hash);

  if (comment) {
    return requestCommentTargetNavigation(to.params.id, comment[1]).then((result) => {
      if (result === 'cancelled') return false;
      // 뒤로 가기에도 대상 스레드는 불러오되, 사용자가 보던 위치를 우선한다.
      if (savedPosition) return savedPosition;
      return { el: result === 'ready' ? to.hash : '#comments-title', top: SCROLL_OFFSET, behavior: 'smooth' };
    });
  }

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
