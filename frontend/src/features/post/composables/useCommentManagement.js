import { onBeforeUnmount, ref, watch } from 'vue';
import { updatePostComment, deletePostComment } from '../api/postApi';
import { dismissToast, notifyError } from '../../../shared/toast/toastStore';
import { getAuthenticationGeneration } from '../../../shared/api/blogApiClient';

export function useCommentManagement({ postId, items, total, member }) {
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
	let active = true;
	let editRequest = null;
	let deleteRequest = null;
	let editErrorToast = null;
	let deleteErrorToast = null;
	let identity = readIdentity();

	function readIdentity() {
		return {
			postId: postId.value,
			memberId: member.value?.id ?? null,
			authentication: getAuthenticationGeneration(),
		};
	}

	function clearEditError() {
		editError.value = '';
		if (editErrorToast !== null) {
			dismissToast(editErrorToast);
			editErrorToast = null;
		}
	}

	function clearDeleteError() {
		deleteError.value = '';
		if (deleteErrorToast !== null) {
			dismissToast(deleteErrorToast);
			deleteErrorToast = null;
		}
	}

	function resetManagement() {
		revision += 1;
		editRevision += 1;
		deleteRevision += 1;
		editRequest = null;
		deleteRequest = null;
		editingId.value = null;
		editDraft.value = '';
		deletingId.value = null;
		editPending.value = false;
		deletePending.value = false;
		clearEditError();
		clearDeleteError();
	}

	function synchronizeIdentity() {
		if (!active) {
			return false;
		}
		const next = readIdentity();
		if (next.postId === identity.postId && next.memberId === identity.memberId
				&& next.authentication === identity.authentication) {
			return false;
		}
		identity = next;
		resetManagement();
		return true;
	}

	watch([postId, member], synchronizeIdentity, { flush: 'sync' });
	onBeforeUnmount(() => { active = false; resetManagement(); });

	function isCurrentRequest(request, kind) {
		// 인증 세대 getter만 바뀌어도 응답·finally 경계에서 이전 작업을 끊는다.
		synchronizeIdentity();
		return active && request.revision === revision
			&& request === (kind === 'edit' ? editRequest : deleteRequest);
	}

	function startEdit(comment) {
		synchronizeIdentity();
		if (!active) {
			return;
		}
		editRevision += 1;
		clearEditError();
		deletingId.value = null;
		editingId.value = comment.id;
		editDraft.value = comment.content;
	}

	function cancelEdit() {
		synchronizeIdentity();
		editRevision += 1;
		editingId.value = null;
		editDraft.value = '';
		clearEditError();
	}

	async function saveEdit(comment) {
		if (synchronizeIdentity() || !active) {
			return;
		}
		const content = editDraft.value.trim();

		if (!content || editPending.value) {
			return;
		}

		const request = { revision };
		const requestedEditRevision = editRevision;
		editRequest = request;
		editPending.value = true;
		clearEditError();

		try {
			await updatePostComment(comment.id, content);
			if (!isCurrentRequest(request, 'edit')) {
				return;
			}

			// 목록을 다시 받지 않고 자리에서 바꾼다. 다시 받으면 읽던 위치가 위로 튄다
			comment.content = content;
			comment.edited = true;
			if (requestedEditRevision === editRevision && editDraft.value.trim() === content) {
				cancelEdit();
			}
		} catch (error) {
			if (!isCurrentRequest(request, 'edit') || requestedEditRevision !== editRevision) {
				return;
			}
			editError.value = error.message ?? '고치지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
			editErrorToast = notifyError(editError.value);
		} finally {
			if (isCurrentRequest(request, 'edit')) {
				editRequest = null;
				editPending.value = false;
			}
		}
	}

	function askDelete(comment) {
		synchronizeIdentity();
		if (!active) {
			return;
		}
		deleteRevision += 1;
		cancelEdit();
		deletingId.value = comment.id;
	}

	function cancelDelete() {
		synchronizeIdentity();
		deleteRevision += 1;
		deletingId.value = null;
	}

	async function confirmDelete(target, parent = null) {
		if (synchronizeIdentity() || !active) {
			return;
		}
		if (deletePending.value) {
			return;
		}

		const request = { revision };
		const requestedDeleteRevision = deleteRevision;
		deleteRequest = request;
		deletePending.value = true;
		clearDeleteError();

		try {
			await deletePostComment(target.id);
			if (!isCurrentRequest(request, 'delete')) {
				return;
			}

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
			if (requestedDeleteRevision === deleteRevision) {
				deletingId.value = null;
			}
		} catch (error) {
			if (!isCurrentRequest(request, 'delete') || requestedDeleteRevision !== deleteRevision) {
				return;
			}
			deleteError.value = error.message ?? '지우지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
			deleteErrorToast = notifyError(deleteError.value);
			if (requestedDeleteRevision === deleteRevision) {
				deletingId.value = null;
			}
		} finally {
			if (isCurrentRequest(request, 'delete')) {
				deleteRequest = null;
				deletePending.value = false;
			}
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
