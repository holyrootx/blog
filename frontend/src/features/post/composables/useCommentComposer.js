import { nextTick, ref } from 'vue';
import { createPostComment } from '../api/postApi';
import { notifyError } from '../../../shared/toast/toastStore';

export function useCommentComposer({ postId, items, total, member, replyInput }) {
  const submitting = ref(false);
  const replySubmitting = ref(false);
  const draft = ref('');
  const replyTargetId = ref(null);
  const replyDraft = ref('');
  const formError = ref('');
  const replyError = ref('');

  function toggleReply(commentId) {
    replyTargetId.value = replyTargetId.value === commentId ? null : commentId;
    replyDraft.value = '';
    replyError.value = '';

    if (replyTargetId.value === null) {
      return;
    }

    // 답글 칸은 지금 막 만들어져서 아직 화면에 없다. 그려진 뒤에 커서를 옮긴다.
    // v-for 안이라 ref 는 배열로 들어오는데, 열려 있는 답글 폼은 언제나 하나뿐이다
    nextTick(() => {
      const input = Array.isArray(replyInput.value) ? replyInput.value[0] : replyInput.value;

      input?.focus();
    });
  }

  async function submitComment() {
    const content = draft.value.trim();

    if (!content || submitting.value) {
      formError.value = content ? '' : '댓글 내용을 입력해 주세요.';
      return;
    }

    submitting.value = true;
    formError.value = '';

    try {
      const created = await createPostComment(postId.value, { content });
      items.value = [toCreatedComment(created?.id, content), ...items.value];
      total.value += 1;
      draft.value = '';
    } catch (error) {
      formError.value = error?.message ?? '댓글을 등록하지 못했습니다.';
      notifyError(formError.value);
    } finally {
      submitting.value = false;
    }
  }

  async function submitReply(parentId) {
    const content = replyDraft.value.trim();

    if (!content || replySubmitting.value) {
      replyError.value = content ? '' : '답글 내용을 입력해 주세요.';
      return;
    }

    replySubmitting.value = true;
    replyError.value = '';

    try {
      const created = await createPostComment(postId.value, { content, parentId });
      const parent = items.value.find((comment) => comment.id === parentId);

      if (parent) {
        parent.replies = [...parent.replies, toCreatedComment(created?.id, content)];
      }

      total.value += 1;
      replyDraft.value = '';
      replyTargetId.value = null;
    } catch (error) {
      replyError.value = error?.message ?? '답글을 등록하지 못했습니다.';
      notifyError(replyError.value);
    } finally {
      replySubmitting.value = false;
    }
  }

  function toCreatedComment(id, content) {
    return {
      id,
      author: member.value?.nickname ?? '',
      createdAt: '방금 전',
      content,
      isAuthor: member.value?.role === 'ADMIN',
      deleted: false,
      likeCount: 0,
      dislikeCount: 0,
      likedByMe: false,
      dislikedByMe: false,
      // 방금 내가 쓴 것이다. 안 넣으면 새로고침 전까지 내 댓글에 신고 단추가 보인다
      mine: true,
      hiddenReplyCount: 0,
      replies: [],
    };
  }

  return {
    submitting,
    replySubmitting,
    draft,
    replyTargetId,
    replyDraft,
    formError,
    replyError,
    toggleReply,
    submitComment,
    submitReply,
  };
}
