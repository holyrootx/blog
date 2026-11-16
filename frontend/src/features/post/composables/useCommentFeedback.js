import { onBeforeUnmount, ref, watch } from 'vue';
import { removeCommentReaction, setCommentReaction, reportComment } from '../api/postApi';
import { dismissToast, notifyError, notifySuccess } from '../../../shared/toast/toastStore';
import { getAuthenticationGeneration } from '../../../shared/api/blogApiClient';

export function useCommentFeedback({ postId, member, isSignedIn, goToLogin }) {
	const reactionRequests = ref(new Map());
	const reactionError = ref('');
	const reportTarget = ref(null);
	const reportPending = ref(false);
	const reportError = ref('');
	const reportDone = ref('');

	let active = true;
	let requestSequence = 0;
	let reportRequest = null;
	let reportRevision = 0;
	let identity = readIdentity();
	const errorToasts = new Set();

	function readIdentity() {
		return { postId: postId.value, memberId: member.value?.id ?? null, authentication: getAuthenticationGeneration() };
	}

	function clearErrorToasts() {
		errorToasts.forEach(id => dismissToast(id));
		errorToasts.clear();
	}

	function synchronizeIdentity() {
		if (!active) {
			return;
		}
		const next = readIdentity();
		if (next.postId === identity.postId && next.memberId === identity.memberId
				&& next.authentication === identity.authentication) {
			return;
		}
		identity = next;
		reactionRequests.value = new Map();
		reactionError.value = '';
		reportRequest = null;
		reportRevision += 1;
		reportTarget.value = null;
		reportPending.value = false;
		reportError.value = '';
		reportDone.value = '';
		clearErrorToasts();
	}

	// 같은 ID의 재확인에서도 인증 세대를 읽고, 닉네임만 바뀌면 진행을 유지한다.
	watch([postId, member, () => member.value?.id ?? null], synchronizeIdentity, { flush: 'sync' });
	onBeforeUnmount(() => { active = false; reportRequest = null; clearErrorToasts(); });

	function isCurrent(request, token) {
		// 인증 세대는 getter라 반응형 알림 없이 바뀔 수 있어 응답과 다음 동작에서도 확인한다.
		synchronizeIdentity();
		return active && request.identity === identity && request.token === token;
	}

	function showError(message) {
		errorToasts.add(notifyError(message));
	}

	function isReactionPending(commentId) {
		synchronizeIdentity();
		return reactionRequests.value.has(commentId);
	}

	function openReport(comment) {
		synchronizeIdentity();
		if (!active) {
			return;
		}
		reportRevision += 1;
		reportError.value = '';
		reportDone.value = '';
		reportTarget.value = comment;
	}

	function closeReport() {
		synchronizeIdentity();
		if (!active) {
			return;
		}
		reportRevision += 1;
		reportTarget.value = null;
		reportError.value = '';
	}

	async function submitReport({ reason, detail }) {
		synchronizeIdentity();
		if (!active || reportPending.value || reportTarget.value === null) {
			return;
		}

		const target = reportTarget.value;
		const request = { identity, token: ++requestSequence };
		const requestedReportRevision = reportRevision;
		reportRequest = request.token;
		reportPending.value = true;
		reportError.value = '';

		try {
			await reportComment(target.id, { reason, detail });
			if (!isCurrent(request, reportRequest)) {
				return;
			}

			// 목록을 다시 받지 않고 자리에서 바꾼다. 다시 받으면 읽던 위치가 위로 튄다
			target.reportedByMe = true;
			if (requestedReportRevision !== reportRevision) {
				return;
			}
			closeReport();
			// 신고 수는 화면에 안 보인다. 보이면 그 자체로 낙인이 되고,
			// 몰려서 신고하면 숫자가 오르는 것이 보여 재미가 붙는다
			reportDone.value = '신고를 접수했습니다. 확인 뒤 처리하겠습니다.';
			// 신고는 눌러도 화면이 거의 안 바뀐다. 접수됐다는 말을 한 번은 크게 해 준다
			notifySuccess('신고가 접수되었습니다.');
		} catch (error) {
			if (!isCurrent(request, reportRequest) || requestedReportRevision !== reportRevision) {
				return;
			}
			// 이미 신고했거나 내 댓글인 경우 서버가 이유를 준다. 그대로 보여 준다
			reportError.value = error.message ?? '신고하지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
			showError(reportError.value);
		} finally {
			if (isCurrent(request, reportRequest)) {
				reportRequest = null;
				reportPending.value = false;
			}
		}
	}

	async function reactToComment(comment, type) {
		synchronizeIdentity();
		if (!active) {
			return;
		}
		if (!isSignedIn.value) {
			goToLogin();
			return;
		}

		if (isReactionPending(comment.id)) {
			return;
		}

		const request = { identity, token: ++requestSequence };
		reactionRequests.value.set(comment.id, request.token);
		reactionError.value = '';

		try {
			const active = type === 'LIKE' ? comment.likedByMe : comment.dislikedByMe;
			const reaction = active
				? await removeCommentReaction(comment.id, type)
				: await setCommentReaction(comment.id, type);

			if (!isCurrent(request, reactionRequests.value.get(comment.id))) {
				return;
			}
			comment.likeCount = Number(reaction?.likeCount ?? 0);
			comment.dislikeCount = Number(reaction?.dislikeCount ?? 0);
			comment.likedByMe = Boolean(reaction?.likedByMe);
			comment.dislikedByMe = Boolean(reaction?.dislikedByMe);
		} catch (error) {
			if (!isCurrent(request, reactionRequests.value.get(comment.id))) {
				return;
			}
			reactionError.value = error?.message ?? '댓글 반응을 저장하지 못했습니다.';
			showError(reactionError.value);
		} finally {
			// finally 에서 return 하면 앞에서 던진 예외를 삼킨다. 늦은 응답이면 정리만 건너뛴다
			if (isCurrent(request, reactionRequests.value.get(comment.id))) {
				reactionRequests.value.delete(comment.id);
			}
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
