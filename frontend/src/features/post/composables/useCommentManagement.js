import { ref } from 'vue';
import { updatePostComment, deletePostComment } from '../api/postApi';
import { notifyError } from '../../../shared/toast/toastStore';

export function useCommentManagement({ items, total }) {
  const editingId = ref(null);
  const editDraft = ref('');
  const editPending = ref(false);
  const editError = ref('');
  const deletingId = ref(null);
  const deletePending = ref(false);
  const deleteError = ref('');

  function startEdit(comment) {
    editError.value = '';
    deletingId.value = null;
    editingId.value = comment.id;
    editDraft.value = comment.content;
  }

  function cancelEdit() {
    editingId.value = null;
    editDraft.value = '';
    editError.value = '';
  }

  async function saveEdit(comment) {
    const content = editDraft.value.trim();

    if (!content || editPending.value) {
      return;
    }

    editPending.value = true;
    editError.value = '';

    try {
      await updatePostComment(comment.id, content);

      // 목록을 다시 받지 않고 자리에서 바꾼다. 다시 받으면 읽던 위치가 위로 튄다
      comment.content = content;
      comment.edited = true;
      cancelEdit();
    } catch (error) {
      editError.value = error.message ?? '고치지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
      notifyError(editError.value);
    } finally {
      editPending.value = false;
    }
  }

  function askDelete(comment) {
    cancelEdit();
    deletingId.value = comment.id;
  }

  function cancelDelete() {
    deletingId.value = null;
  }

  async function confirmDelete(target, parent = null) {
    if (deletePending.value) {
      return;
    }

    deletePending.value = true;
    deleteError.value = '';

    try {
      await deletePostComment(target.id);

      if (parent) {
        parent.replies = parent.replies.filter((reply) => reply.id !== target.id);
      } else if (target.replies.length > 0) {
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
      deletingId.value = null;
    } catch (error) {
      deleteError.value = error.message ?? '지우지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
      notifyError(deleteError.value);
      deletingId.value = null;
    } finally {
      deletePending.value = false;
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
