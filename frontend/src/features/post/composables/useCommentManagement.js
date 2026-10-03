import { onBeforeUnmount, ref, watch } from 'vue';
import { updatePostComment, deletePostComment } from '../api/postApi';
import { notifyError } from '../../../shared/toast/toastStore';

export function useCommentManagement({ postId, items, total }) {
  const editingId = ref(null);
  const editDraft = ref('');
  const editPending = ref(false);
  const editError = ref('');
  const deletingId = ref(null);
  const deletePending = ref(false);
  const deleteError = ref('');

  let revision = 0;
  let editRevision = 0;
  let deleteRevision = 0;
  watch(postId, () => {
    revision += 1;
    cancelEdit();
    cancelDelete();
    editPending.value = false;
    deletePending.value = false;
    deleteError.value = '';
  }, { flush: 'sync' });
  onBeforeUnmount(() => { revision += 1; });

  function startEdit(comment) {
    editRevision += 1;
    editError.value = '';
    deletingId.value = null;
    editingId.value = comment.id;
    editDraft.value = comment.content;
  }

  function cancelEdit() {
    editRevision += 1;
    editingId.value = null;
    editDraft.value = '';
    editError.value = '';
  }

  async function saveEdit(comment) {
    const content = editDraft.value.trim();

    if (!content || editPending.value) {
      return;
    }

    const requestedRevision = revision;
    const requestedEditRevision = editRevision;
    editPending.value = true;
    editError.value = '';

    try {
      await updatePostComment(comment.id, content);
      if (requestedRevision !== revision) return;

      // 목록을 다시 받지 않고 자리에서 바꾼다. 다시 받으면 읽던 위치가 위로 튄다
      comment.content = content;
      comment.edited = true;
      if (requestedEditRevision === editRevision && editDraft.value.trim() === content) cancelEdit();
    } catch (error) {
      if (requestedRevision !== revision || requestedEditRevision !== editRevision) return;
      editError.value = error.message ?? '고치지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
      notifyError(editError.value);
    } finally {
      if (requestedRevision === revision) editPending.value = false;
    }
  }

  function askDelete(comment) {
    deleteRevision += 1;
    cancelEdit();
    deletingId.value = comment.id;
  }

  function cancelDelete() {
    deleteRevision += 1;
    deletingId.value = null;
  }

  async function confirmDelete(target, parent = null) {
    if (deletePending.value) {
      return;
    }

    const requestedRevision = revision;
    const requestedDeleteRevision = deleteRevision;
    deletePending.value = true;
    deleteError.value = '';

    try {
      await deletePostComment(target.id);
      if (requestedRevision !== revision) return;

      if (parent) {
        parent.replies = parent.replies.filter((reply) => reply.id !== target.id);
      } else if (target.replies.length > 0 || target.replyHasNext) {
        target.deleted = true;
        target.content = '';
        target.author = '';
        target.mine = false;
        // 내가 지운 것이다. 운영자가 가린 것으로 보이면 안 된다
        target.hiddenByAdmin = false;
      } else {
        items.value = items.value.filter((comment) => comment.id !== target.id);
      }

      total.value = Math.max(0, total.value - 1);
      if (requestedDeleteRevision === deleteRevision) deletingId.value = null;
    } catch (error) {
      if (requestedRevision !== revision || requestedDeleteRevision !== deleteRevision) return;
      deleteError.value = error.message ?? '지우지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
      notifyError(deleteError.value);
      if (requestedDeleteRevision === deleteRevision) deletingId.value = null;
    } finally {
      if (requestedRevision === revision) deletePending.value = false;
    }
  }

  return {
    editingId,
    editDraft,
    editPending,
    editError,
    deletingId,
    deletePending,
    deleteError,
    startEdit,
    cancelEdit,
    saveEdit,
    askDelete,
    cancelDelete,
    confirmDelete,
  };
}
