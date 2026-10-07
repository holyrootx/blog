import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { commentTargetNavigation } from '../../../app/router/commentTargetNavigation';
import { getCommentContext } from '../api/postApi';

export function useCommentTarget({ postId, initialLoading, comments, mergeTarget, clearTarget }) {
  const targetLoading = ref(false);
  const targetMessage = ref('');
  const targetRetryable = ref(false);
  let controller;
  let revision = 0;

  function reset() {
    revision += 1;
    controller?.abort();
    clearTarget();
    targetLoading.value = false;
    targetMessage.value = '';
    targetRetryable.value = false;
  }

  async function loadTarget(navigation, retry = false) {
    const requested = ++revision;
    controller?.abort();
    controller = new AbortController();
    const { signal } = controller;
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
