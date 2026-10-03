import { onBeforeUnmount, ref, watch } from 'vue';
import { removeCommentReaction, setCommentReaction, reportComment } from '../api/postApi';
import { notifyError, notifySuccess } from '../../../shared/toast/toastStore';

export function useCommentFeedback({ postId, isSignedIn, goToLogin }) {
  const reactionPendingIds = ref(new Set());
  const reactionError = ref('');
  const reportTarget = ref(null);
  const reportPending = ref(false);
  const reportError = ref('');
  const reportDone = ref('');

  let revision = 0;
  let reportRevision = 0;
  watch(postId, () => {
    revision += 1;
    closeReport();
    reportPending.value = false;
    reportDone.value = '';
    reactionPendingIds.value = new Set();
    reactionError.value = '';
  }, { flush: 'sync' });
  onBeforeUnmount(() => { revision += 1; });

  function isReactionPending(commentId) {
    return reactionPendingIds.value.has(commentId);
  }

  function openReport(comment) {
    reportRevision += 1;
    reportError.value = '';
    reportDone.value = '';
    reportTarget.value = comment;
  }

  function closeReport() {
    reportRevision += 1;
    reportTarget.value = null;
    reportError.value = '';
  }

  async function submitReport({ reason, detail }) {
    if (reportPending.value || reportTarget.value === null) {
      return;
    }

    const target = reportTarget.value;
    const requestedRevision = revision;
    const requestedReportRevision = reportRevision;
    reportPending.value = true;
    reportError.value = '';

    try {
      await reportComment(target.id, { reason, detail });
      if (requestedRevision !== revision) return;

      // 목록을 다시 받지 않고 자리에서 바꾼다. 다시 받으면 읽던 위치가 위로 튄다
      target.reportedByMe = true;
      if (requestedReportRevision !== reportRevision) return;
      closeReport();
      // 신고 수는 화면에 안 보인다. 보이면 그 자체로 낙인이 되고,
      // 몰려서 신고하면 숫자가 오르는 것이 보여 재미가 붙는다
      reportDone.value = '신고를 접수했습니다. 확인 뒤 처리하겠습니다.';
      // 신고는 눌러도 화면이 거의 안 바뀐다. 접수됐다는 말을 한 번은 크게 해 준다
      notifySuccess('신고가 접수되었습니다.');
    } catch (error) {
      if (requestedRevision !== revision || requestedReportRevision !== reportRevision) return;
      // 이미 신고했거나 내 댓글인 경우 서버가 이유를 준다. 그대로 보여 준다
      reportError.value = error.message ?? '신고하지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
      notifyError(reportError.value);
    } finally {
      if (requestedRevision === revision) reportPending.value = false;
    }
  }

  async function reactToComment(comment, type) {
    if (!isSignedIn.value) {
      goToLogin();
      return;
    }

    if (isReactionPending(comment.id)) {
      return;
    }

    const requestedRevision = revision;
    reactionPendingIds.value = new Set([...reactionPendingIds.value, comment.id]);
    reactionError.value = '';

    try {
      const active = type === 'LIKE' ? comment.likedByMe : comment.dislikedByMe;
      const reaction = active
        ? await removeCommentReaction(comment.id, type)
        : await setCommentReaction(comment.id, type);

      if (requestedRevision !== revision) return;
      comment.likeCount = Number(reaction?.likeCount ?? 0);
      comment.dislikeCount = Number(reaction?.dislikeCount ?? 0);
      comment.likedByMe = Boolean(reaction?.likedByMe);
      comment.dislikedByMe = Boolean(reaction?.dislikedByMe);
    } catch (error) {
      if (requestedRevision !== revision) return;
      reactionError.value = error?.message ?? '댓글 반응을 저장하지 못했습니다.';
      notifyError(reactionError.value);
    } finally {
      if (requestedRevision !== revision) return;
      const pendingIds = new Set(reactionPendingIds.value);
      pendingIds.delete(comment.id);
      reactionPendingIds.value = pendingIds;
    }
  }

  return {
    reactionError,
    reportTarget,
    reportPending,
    reportError,
    reportDone,
    isReactionPending,
    openReport,
    closeReport,
    submitReport,
    reactToComment,
  };
}
