import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
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

  async function loadMore() {
    if (loading.value || !hasNext.value || nextCursor.value === null) return;
    loading.value = true;
    const requestedRevision = revision;
    try {
      const nextPage = await getPostComments(postId.value, { cursor: nextCursor.value });
      if (requestedRevision !== revision) return;
      if (targetOverlay && nextPage.items.some((item) => item.id === targetOverlay.parentId)) {
        targetOverlay.addedParent = false;
        const listed = nextPage.items.find((item) => item.id === targetOverlay.parentId);
        listed.replies.forEach((reply) => targetOverlay.addedReplyIds.delete(reply.id));
      }
      items.value = mergeCommentItems(items.value, nextPage.items);
      total.value = nextPage.total;
      nextCursor.value = nextPage.nextCursor;
      hasNext.value = nextPage.hasNext;
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
      if (entries[0]?.isIntersecting) loadMore();
    });
    observeSentinel();
  });
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

  return { total, items, hasNext, loading, sentinel, loadMore, loadReplies, replyLoadingIds, mergeTarget, clearTarget };
}
