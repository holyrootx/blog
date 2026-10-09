import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { commentTargetNavigation } from '../../../app/router/commentTargetNavigation';
import { getCommentContext } from '../api/postApi';

/** 부드러운 스크롤은 프레임마다 scroll 을 낸다. 이만큼 조용하면 멈춘 것으로 본다 */
const SCROLL_QUIET_MS = 150;
/** 스크롤 이벤트를 놓쳐도 자동 불러오기가 영영 멈춰 있지 않게 하는 상한 */
const SCROLL_SETTLE_LIMIT_MS = 2000;

/**
 * 라우터가 대상으로 옮긴 스크롤이 끝날 때까지 기다린다.
 *
 * scrollend 는 지원하지 않는 브라우저가 있어 쓰지 않는다. 이미 그 자리라 스크롤이 아예 없을
 * 수도 있으니, 첫 이벤트를 기다리지 않고 한 프레임 뒤부터 조용함을 잰다.
 */
function waitForScrollSettle() {
  return new Promise((resolve) => {
    let quietTimer;
    const limitTimer = setTimeout(done, SCROLL_SETTLE_LIMIT_MS);
    function done() {
      clearTimeout(quietTimer);
      clearTimeout(limitTimer);
      window.removeEventListener('scroll', restart);
      resolve();
    }
    function restart() {
      clearTimeout(quietTimer);
      quietTimer = setTimeout(done, SCROLL_QUIET_MS);
    }
    window.addEventListener('scroll', restart, { passive: true });
    requestAnimationFrame(restart);
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
