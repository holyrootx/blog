import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { commentTargetNavigation } from '../../../app/router/commentTargetNavigation';
import { getCommentContext } from '../api/postApi';

/** 스크롤 위치가 이만큼 그대로 머물러야 멈춘 것으로 본다 */
const SCROLL_QUIET_MS = 150;
/**
 * 시간과 함께 "그대로였던 프레임 수" 도 센다. 시간만 재면 느린 기기(CI 포함)에서 한 프레임이
 * 150ms 를 넘길 때, 아직 움직이는 스크롤을 멈췄다고 보고 자동 불러오기를 너무 일찍 푼다
 */
const SCROLL_STILL_FRAMES = 10;
/** 끝 신호를 놓쳐도 자동 불러오기가 영영 멈춰 있지 않게 하는 상한. 느린 기기의 부드러운 스크롤보다 길게 */
const SCROLL_SETTLE_LIMIT_MS = 4000;

/**
 * 라우터가 대상으로 옮긴 스크롤이 끝날 때까지 기다린다.
 *
 * 스크롤 끝 신호(scrollend)를 주는 브라우저에서는, 스크롤이 움직이기 시작한 뒤에는 그 신호만 믿는다.
 * 바쁜 기기에서는 부드러운 스크롤이 몇 프레임씩 멈췄다 가기도 해서, 조용함만으로는 끝을 잘못 본다.
 * 신호가 없는 브라우저는 프레임마다 위치를 재서 한동안 그대로이면 끝난 것으로 본다.
 * 이미 그 자리라 스크롤이 아예 없으면 끝 신호도 오지 않으므로, 움직임이 없을 때는 조용함으로 끝낸다.
 */
function waitForScrollSettle() {
  return new Promise((resolve) => {
    const endSignal = 'onscrollend' in window;
    let last = window.scrollY;
    let lastMove = performance.now();
    let stillFrames = 0;
    let moved = false;
    let frame = requestAnimationFrame(tick);
    const limitTimer = setTimeout(done, SCROLL_SETTLE_LIMIT_MS);

    if (endSignal) {
      window.addEventListener('scrollend', done);
    }

    function done() {
      cancelAnimationFrame(frame);
      clearTimeout(limitTimer);
      window.removeEventListener('scrollend', done);
      resolve();
    }

    function tick(now) {
      if (Math.abs(window.scrollY - last) >= 1) {
        last = window.scrollY;
        lastMove = now;
        stillFrames = 0;
        moved = true;
      } else {
        stillFrames += 1;
      }

      const quiet = stillFrames >= SCROLL_STILL_FRAMES && now - lastMove >= SCROLL_QUIET_MS;

      if (quiet && !(endSignal && moved)) {
        done();
      } else {
        frame = requestAnimationFrame(tick);
      }
    }
  });
}

export function useCommentTarget({
  postId, initialLoading, comments, mergeTarget, clearTarget, pauseAutoLoad, resumeAutoLoad,
}) {
  const targetLoading = ref(false);
  const targetMessage = ref('');
  const targetRetryable = ref(false);
  let controller;
  let revision = 0;

  function reset() {
    revision += 1;
    controller?.abort();
    clearTarget();
    resumeAutoLoad();
    targetLoading.value = false;
    targetMessage.value = '';
    targetRetryable.value = false;
  }

  async function loadTarget(navigation, retry = false) {
    const requested = ++revision;
    controller?.abort();
    controller = new AbortController();
    const { signal } = controller;
    // 대상을 받아 스크롤이 끝날 때까지 목록 끝 자동 불러오기를 멈춘다(useCommentFeed.pauseAutoLoad)
    pauseAutoLoad();
    targetLoading.value = true;
    targetMessage.value = '';
    targetRetryable.value = false;
    const current = () => requested === revision && !signal.aborted
      && commentTargetNavigation.value === navigation && String(postId.value) === navigation.postId;

    try {
      const context = await getCommentContext(navigation.postId, navigation.commentId, { signal });
      if (!current()) return;
      mergeTarget(context.item);
      await nextTick();
      if (!current()) return;
      const element = document.getElementById(`comment-${navigation.commentId}`);
      if (element) {
        navigation.finish('ready');
        if (retry) element.scrollIntoView({ block: 'center', behavior: 'smooth' });
      } else {
        targetMessage.value = '알림의 댓글을 표시하지 못했습니다. 다시 시도해 주세요.';
        targetRetryable.value = true;
        navigation.finish('error');
      }
    } catch (error) {
      if (!current()) return;
      const unavailable = error.status === 404 || error.status === 410;
      targetMessage.value = unavailable
        ? '알림의 댓글을 볼 수 없습니다. 삭제되었거나 운영자가 숨긴 댓글일 수 있습니다.'
        : '알림의 댓글을 불러오지 못했습니다. 다시 시도해 주세요.';
      targetRetryable.value = !unavailable;
      navigation.finish(unavailable ? 'unavailable' : 'error');
    } finally {
      if (current()) targetLoading.value = false;
    }

    // 여기까지 왔으면 라우터가 대상(또는 댓글 제목)으로 스크롤을 시작했다. 다른 대상으로
    // 넘어갔다면 그쪽이 멈춤을 이어받았으므로 풀지 않는다
    await waitForScrollSettle();
    if (requested === revision) resumeAutoLoad();
  }

  watch([commentTargetNavigation, postId, initialLoading, comments], ([navigation, id, loading]) => {
    reset();
    if (navigation && navigation.postId === String(id) && !loading) loadTarget(navigation);
  }, { immediate: true, flush: 'post' });

  onBeforeUnmount(() => {
    const navigation = commentTargetNavigation.value;
    if (navigation?.postId === String(postId.value)) navigation.finish('cancelled');
    reset();
  });

  function retryTarget() {
    const navigation = commentTargetNavigation.value;
    if (navigation && !targetLoading.value && navigation.postId === String(postId.value)) loadTarget(navigation, true);
  }

  return { targetLoading, targetMessage, targetRetryable, retryTarget };
}
