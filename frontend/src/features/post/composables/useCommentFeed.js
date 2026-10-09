import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { getCommentReplies, getPostComments } from '../api/postApi';
import { notifyError } from '../../../shared/toast/toastStore';
import { mergeCommentItems } from '../data/commentItems';

export function useCommentFeed({ postId, comments }) {
  const total = ref(0);
  const items = ref([]);
  const nextCursor = ref(null);
  const hasNext = ref(false);
  const loading = ref(false);
  const sentinel = ref(null);
  const replyLoadingIds = ref(new Set());
  let observer = null;
  let revision = 0;
  let targetOverlay = null;
  let autoLoadPaused = false;

  async function loadMore() {
    if (loading.value || !hasNext.value || nextCursor.value === null) return;
    loading.value = true;
    const requestedRevision = revision;
    try {
      const nextPage = await getPostComments(postId.value, { cursor: nextCursor.value });
      if (requestedRevision !== revision) return;
      const anchor = targetAnchor();
      if (targetOverlay && nextPage.items.some((item) => item.id === targetOverlay.parentId)) {
        targetOverlay.addedParent = false;
        const listed = nextPage.items.find((item) => item.id === targetOverlay.parentId);
        listed.replies.forEach((reply) => targetOverlay.addedReplyIds.delete(reply.id));
      }
      await keepInPlace(anchor, () => {
        items.value = mergeCommentItems(items.value, nextPage.items);
        total.value = nextPage.total;
        nextCursor.value = nextPage.nextCursor;
        hasNext.value = nextPage.hasNext;
      });
    } catch (error) {
      if (requestedRevision === revision) console.error(error);
    } finally {
      if (requestedRevision === revision) loading.value = false;
    }
  }

  async function loadReplies(comment) {
    if (!comment.replyHasNext || replyLoadingIds.value.has(comment.id)) return;
    const requestedRevision = revision;
    replyLoadingIds.value = new Set([...replyLoadingIds.value, comment.id]);
    try {
      const page = await getCommentReplies(postId.value, comment.id, { cursor: comment.replyNextCursor });
      if (requestedRevision !== revision) return;
      if (targetOverlay?.parentId === comment.id) {
        page.items.forEach((reply) => targetOverlay.addedReplyIds.delete(reply.id));
      }
      const ids = new Set(comment.replies.map((reply) => reply.id));
      comment.replies = [...comment.replies, ...page.items.filter((reply) => !ids.has(reply.id))]
        .sort((a, b) => a.id - b.id);
      comment.replyHasNext = page.hasNext;
      comment.replyNextCursor = page.nextCursor;
    } catch (error) {
      if (requestedRevision === revision) notifyError(error?.message ?? '답글을 불러오지 못했습니다. 다시 시도해 주세요.');
    } finally {
      if (requestedRevision === revision) {
        const ids = new Set(replyLoadingIds.value);
        ids.delete(comment.id);
        replyLoadingIds.value = ids;
      }
    }
  }

  watch([postId, comments], ([, page]) => {
    revision += 1;
    targetOverlay = null;
    replyLoadingIds.value = new Set();
    total.value = page.total ?? 0;
    items.value = [...(page.items ?? [])];
    nextCursor.value = page.nextCursor ?? null;
    hasNext.value = Boolean(page.hasNext);
    loading.value = false;
  }, { immediate: true });

  function observeSentinel() {
    observer?.disconnect();
    if (sentinel.value) observer?.observe(sentinel.value);
  }

  onMounted(() => {
    observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting && !autoLoadPaused) loadMore();
    });
    observeSentinel();
  });

  /**
   * 알림 대상으로 스크롤하는 동안 자동 불러오기를 멈춘다.
   *
   * 라우터는 출발할 때 잰 자리로 부드럽게 내려간다. 내려가는 길에 목록 끝 감시점이 화면에
   * 들어오면 다음 페이지가 대상 위에 끼어드는데, 스크롤은 그걸 모르고 옛 자리에서 멈춘다.
   */
  function pauseAutoLoad() {
    autoLoadPaused = true;
  }

  /** 멈춘 사이에 감시점이 이미 화면에 들어와 있으면 관찰자가 다시 알려 주지 않으므로 직접 부른다 */
  function resumeAutoLoad() {
    if (!autoLoadPaused) return;
    autoLoadPaused = false;
    const rect = sentinel.value?.getBoundingClientRect();
    if (rect && rect.top < window.innerHeight && rect.bottom > 0) loadMore();
  }

  /**
   * 목록 순서와 따로 끼워 둔 대상 스레드. 화면에 있거나 이미 지나간 경우만 붙잡는다.
   * 아직 아래에 있으면 사이 페이지가 들어와도 보고 있는 화면은 그대로다.
   */
  function targetAnchor() {
    if (!targetOverlay?.addedParent) return null;
    const element = document.getElementById(`comment-${targetOverlay.parentId}`);
    const top = element?.getBoundingClientRect().top;
    return element && top < window.innerHeight ? { element, top } : null;
  }

  /**
   * 사이 페이지가 대상 위에 들어온 만큼 스크롤을 내려 대상을 제자리에 둔다.
   *
   * 브라우저의 스크롤 고정(overflow-anchor)에 맡기지 않는다. 지원하지 않는 브라우저가 있고,
   * 크롬도 대상보다 위에 있는 댓글을 기준으로 잡아 대상이 그대로 밀린 것을 확인했다(KAN-22).
   * 이 한 번은 브라우저 보정을 끄고 직접 맞춘다 — 둘 다 움직이면 두 배로 밀린다.
   */
  async function keepInPlace(anchor, change) {
    if (!anchor) {
      change();
      return;
    }
    const root = document.documentElement;
    const previous = root.style.overflowAnchor;
    root.style.overflowAnchor = 'none';
    try {
      change();
      await nextTick();
      if (!anchor.element.isConnected) return;
      const moved = anchor.element.getBoundingClientRect().top - anchor.top;
      if (Math.abs(moved) > 1) window.scrollBy({ top: moved, behavior: 'instant' });
    } finally {
      requestAnimationFrame(() => {
        root.style.overflowAnchor = previous;
      });
    }
  }

  watch(sentinel, observeSentinel, { flush: 'post' });
  onBeforeUnmount(() => {
    revision += 1;
    observer?.disconnect();
  });

  function mergeTarget(item) {
    clearTarget();
    const existing = items.value.find((comment) => comment.id === item.id);
    const replyIds = new Set(existing?.replies.map((reply) => reply.id) ?? []);
    targetOverlay = {
      parentId: item.id,
      addedParent: !existing,
      addedReplyIds: new Set(item.replies.filter((reply) => !replyIds.has(reply.id)).map((reply) => reply.id)),
    };
    items.value = mergeCommentItems(items.value, [item]);
  }

  function clearTarget() {
    if (!targetOverlay) return;
    if (targetOverlay.addedParent) {
      items.value = items.value.filter((item) => item.id !== targetOverlay.parentId);
    } else {
      const parent = items.value.find((item) => item.id === targetOverlay.parentId);
      if (parent) parent.replies = parent.replies.filter((reply) => !targetOverlay.addedReplyIds.has(reply.id));
    }
    targetOverlay = null;
  }

  return {
    total, items, hasNext, loading, sentinel, loadMore, loadReplies, replyLoadingIds,
    mergeTarget, clearTarget, pauseAutoLoad, resumeAutoLoad,
  };
}
