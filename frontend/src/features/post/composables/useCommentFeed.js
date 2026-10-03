import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { getPostComments } from '../api/postApi';

export function useCommentFeed({ postId, comments }) {
  const total = ref(0);
  const items = ref([]);
  const nextCursor = ref(null);
  const hasNext = ref(false);
  const loading = ref(false);
  const sentinel = ref(null);
  let observer = null;
  let revision = 0;

  async function loadMore() {
    if (loading.value || !hasNext.value || nextCursor.value === null) return;
    loading.value = true;
    const requestedRevision = revision;
    try {
      const nextPage = await getPostComments(postId.value, { cursor: nextCursor.value });
      if (requestedRevision !== revision) return;
      items.value = [...items.value, ...nextPage.items];
      total.value = nextPage.total;
      nextCursor.value = nextPage.nextCursor;
      hasNext.value = nextPage.hasNext;
    } catch (error) {
      if (requestedRevision === revision) console.error(error);
    } finally {
      if (requestedRevision === revision) loading.value = false;
    }
  }

  watch([postId, comments], ([, page]) => {
    revision += 1;
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

  return { total, items, hasNext, loading, sentinel, loadMore };
}
