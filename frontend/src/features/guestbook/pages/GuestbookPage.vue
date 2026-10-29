<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';

import CommentActions from '../../post/components/CommentActions.vue';
import CommentComposer from '../../post/components/CommentComposer.vue';
import CommentEditForm from '../../post/components/CommentEditForm.vue';
import CommentReportDialog from '../../post/components/CommentReportDialog.vue';
import {
	GUESTBOOK_MAX_LENGTH,
	createGuestbookEntry,
	deleteGuestbookEntry,
	deleteGuestbookReply,
	getGuestbook,
	reportGuestbookEntry,
	saveGuestbookReply,
	updateGuestbookEntry,
} from '../api/guestbookApi';
import { ensureMemberSession, signOutMember, useMemberAuth } from '../../member/data/memberAuthStore';
import { rememberReturnPath } from '../../member/data/memberReturnPath';
import { notifyError, notifySuccess } from '../../../shared/toast/toastStore';
import { formatSeoulDateTime } from '../../../shared/time/seoulTime';

/**
 * 방명록.
 *
 * <p>글 댓글과 같은 모양으로 그린다. 같은 블로그 안에서 말을 남기는 자리가 두 벌의 생김새를
 * 가지면 어느 쪽이 무엇인지 다시 배워야 한다. 다른 점은 셋이다 — 비밀글이 있고, 답글은 운영자만
 * 하나 달며, 이어 붙이지 않고 쪽으로 넘긴다.</p>
 */
const PLACEHOLDER = '다녀가신 흔적을 남겨 주세요. 짧은 인사도 반갑습니다.';
const SKELETON_ROWS = 3;
// 글 목록과 같은 묶음 크기. 쪽 번호가 밀리지 않고 자리가 고정된다
const BLOCK_SIZE = 10;

const route = useRoute();
const router = useRouter();
const { member, isSignedIn, sessionResolved } = useMemberAuth();
const isAdmin = computed(() => member.value?.role === 'ROLE_ADMIN');

const entries = ref([]);
const totalElements = ref(0);
const totalPages = ref(0);
const maxLength = ref(GUESTBOOK_MAX_LENGTH);
const loading = ref(true);
const loaded = ref(false);
const loadError = ref('');

const page = computed(() => {
	const number = Number(route.query.page);
	return Number.isInteger(number) && number > 0 ? number : 1;
});

const blockStart = computed(() => Math.floor((page.value - 1) / BLOCK_SIZE) * BLOCK_SIZE + 1);
const blockEnd = computed(() => Math.min(blockStart.value + BLOCK_SIZE - 1, totalPages.value));
const pageNumbers = computed(() => {
	const numbers = [];
	for (let number = blockStart.value; number <= blockEnd.value; number += 1) {
		numbers.push(number);
	}
	return numbers;
});

let controller = null;

// 알림으로 찾아온 글. 강조해서 어느 글인지 보이게 한다
const targetId = ref(null);
const targetMessage = ref('');
// 서버가 골라 준 쪽을 주소에 적을 때 그 바뀜으로 다시 받지 않게 한 번 건너뛴다
let skipPageWatch = false;

async function load(entryId = null) {
	controller?.abort();
	const current = new AbortController();
	controller = current;
	loading.value = true;
	loadError.value = '';

	if (entryId === null) {
		targetId.value = null;
		targetMessage.value = '';
	}

	try {
		const result = await getGuestbook({ page: page.value, entryId }, { signal: current.signal });
		if (controller !== current) {
			return;
		}

		entries.value = result.items;
		totalElements.value = result.totalElements;
		totalPages.value = result.totalPages;
		maxLength.value = result.maxLength;
		loaded.value = true;

		if (entryId !== null) {
			await revealTarget(entryId, result.page);
			return;
		}

		// 지운 뒤처럼 쪽이 비었는데 앞쪽이 남아 있으면 마지막 쪽으로 간다. 빈 쪽만 보이면 다 사라진 줄 안다
		if (result.items.length === 0 && result.totalPages > 0 && page.value > result.totalPages) {
			await router.replace({ query: { ...route.query, page: String(result.totalPages) } });
		}
	} catch (error) {
		if (controller !== current || error?.name === 'AbortError') {
			return;
		}
		loadError.value = '방명록을 불러오지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
	} finally {
		if (controller === current) {
			loading.value = false;
		}
	}
}

/**
 * 알림으로 찾아온 글을 보인다. 오래된 글은 뒤쪽에 있어서 몇 쪽인지는 서버가 골라 준다.
 * 지웠거나 가려져 못 찾으면 그렇다고 말한다 — 첫 쪽만 보이면 왜 없는지 알 수 없다.
 */
async function revealTarget(entryId, resultPage) {
	const found = entries.value.some((entry) => entry.id === entryId);
	targetId.value = found ? entryId : null;
	targetMessage.value = found ? '' : '알림의 방명록 글을 찾을 수 없습니다. 지워졌거나 가려진 글일 수 있습니다.';

	skipPageWatch = resultPage !== page.value;
	// 해시를 주면 라우터가 그 글이 그려지기를 기다렸다가 헤더에 가리지 않게 옮겨 준다
	await router.replace({
		query: { ...queryForPage(resultPage), entry: undefined },
		hash: found ? `#guestbook-${entryId}` : '',
	});
}

onMounted(async () => {
	// 로그인 확인을 기다렸다 받는다. 먼저 받으면 내 비밀글이 가려진 채 한 번 그려졌다가 다시 바뀐다
	await ensureMemberSession();
	await load(toId(route.query.entry));
});

onBeforeUnmount(() => controller?.abort());

watch(page, () => {
	if (skipPageWatch) {
		skipPageWatch = false;
		return;
	}
	load();
});

// 방명록을 보던 중에 알림을 눌러도 다시 찾는다. 쪽 감시보다 뒤에 두어야 이 요청이 마지막으로 남는다
watch(() => route.query.entry, (value) => {
	const entryId = toId(value);
	if (entryId !== null && loaded.value) {
		load(entryId);
	}
});

// 로그인·로그아웃·계정 교체로 사람이 바뀌면 읽을 수 있는 비밀글과 내 글 표시가 달라진다
watch(() => member.value?.id ?? null, (next, previous) => {
	if (!loaded.value || next === previous) {
		return;
	}
	resetForms();
	load();
});

function queryForPage(number) {
	return { ...route.query, page: number > 1 ? String(number) : undefined };
}

function toId(value) {
	const id = Number(value);
	return Number.isInteger(id) && id > 0 ? id : null;
}

function avatarInitial(name) {
	return Array.from(name ?? '')[0] ?? '';
}

function goToLogin() {
	// 제공자 화면을 다녀오면 라우터 상태가 남지 않는다. 적어 두지 않으면 돌아왔을 때 홈으로 떨어진다
	rememberReturnPath(route.fullPath);
	router.push({ name: 'member-login' });
}

// ── 쓰기 ──────────────────────────────

const draft = ref('');
const secret = ref(false);
const submitting = ref(false);
const formError = ref('');

async function submit() {
	const content = draft.value.trim();
	if (!content) {
		formError.value = '방명록 내용을 입력해 주세요.';
		return;
	}
	if (submitting.value) {
		return;
	}

	submitting.value = true;
	formError.value = '';

	try {
		await createGuestbookEntry({ content, secret: secret.value });
		draft.value = '';
		secret.value = false;
		notifySuccess('방명록을 남겼습니다.');

		// 새 글은 1쪽 맨 위에 붙는다. 다른 쪽을 보던 중이면 그리로 옮긴다
		if (page.value === 1) {
			await load();
		} else {
			await router.push({ query: queryForPage(1) });
		}
	} catch (error) {
		formError.value = error?.message ?? '방명록을 남기지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
	} finally {
		submitting.value = false;
	}
}

const signingOut = ref(false);

async function signOut() {
	if (signingOut.value) {
		return;
	}
	signingOut.value = true;

	try {
		await signOutMember();
	} catch {
		notifyError('로그아웃을 확인하지 못했습니다. 다시 시도해 주세요.');
		return;
	} finally {
		signingOut.value = false;
	}

	// 로그아웃한 사람 이름으로 올라갈 글이 칸에 남아 있으면 안 된다
	draft.value = '';
	secret.value = false;
}

// ── 내 글 고치기·지우기 ─────────────────

const editingId = ref(null);
const editDraft = ref('');
const editSecret = ref(false);
const editPending = ref(false);
const editError = ref('');
const deletingId = ref(null);
const deletePending = ref(false);
const actionError = ref('');

function startEdit(entry) {
	editingId.value = entry.id;
	editDraft.value = entry.content;
	editSecret.value = entry.secret;
	editError.value = '';
	deletingId.value = null;
}

function cancelEdit() {
	editingId.value = null;
}

async function saveEdit(entry) {
	const content = editDraft.value.trim();
	if (!content || editPending.value) {
		return;
	}

	editPending.value = true;
	editError.value = '';

	try {
		await updateGuestbookEntry(entry.id, { content, secret: editSecret.value });
		// 다시 받지 않고 자리에서 바꾼다. 다시 받으면 읽던 위치가 튄다
		if (entry.content !== content) {
			entry.edited = true;
		}
		entry.content = content;
		entry.secret = editSecret.value;
		editingId.value = null;
	} catch (error) {
		editError.value = error?.message ?? '고치지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
	} finally {
		editPending.value = false;
	}
}

function askDelete(entry) {
	deletingId.value = entry.id;
	editingId.value = null;
	actionError.value = '';
}

function cancelDelete() {
	deletingId.value = null;
}

async function confirmDelete(entry) {
	if (deletePending.value) {
		return;
	}
	deletePending.value = true;
	actionError.value = '';

	try {
		await deleteGuestbookEntry(entry.id);
		deletingId.value = null;
		notifySuccess('방명록 글을 지웠습니다.');
		await load();
	} catch (error) {
		actionError.value = error?.message ?? '지우지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
	} finally {
		deletePending.value = false;
	}
}

// ── 신고 ──────────────────────────────

const reportTarget = ref(null);
const reportPending = ref(false);
const reportError = ref('');
const reportDone = ref('');

function openReport(entry) {
	if (!isSignedIn.value) {
		goToLogin();
		return;
	}
	reportTarget.value = entry;
	reportError.value = '';
	reportDone.value = '';
}

function closeReport() {
	reportTarget.value = null;
	reportError.value = '';
}

async function submitReport({ reason, detail }) {
	const target = reportTarget.value;
	if (!target || reportPending.value) {
		return;
	}

	reportPending.value = true;
	reportError.value = '';

	try {
		await reportGuestbookEntry(target.id, { reason, detail });
		target.reportedByMe = true;
		closeReport();
		reportDone.value = '신고를 접수했습니다. 확인 뒤 처리하겠습니다.';
		notifySuccess('신고가 접수되었습니다.');
	} catch (error) {
		reportError.value = error?.message ?? '신고하지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
	} finally {
		reportPending.value = false;
	}
}

// ── 운영자 답글 ─────────────────────────
// 화면의 운영자 판단은 단추를 보여 줄지 정하는 데만 쓴다. 실제로 막는 것은 서버다

const replyTargetId = ref(null);
const replyDraft = ref('');
const replyPending = ref(false);
const replyError = ref('');
const deletingReplyId = ref(null);

function openReply(entry) {
	replyTargetId.value = entry.id;
	replyDraft.value = entry.reply?.content ?? '';
	replyError.value = '';
	deletingReplyId.value = null;
}

function cancelReply() {
	replyTargetId.value = null;
}

async function saveReply(entry) {
	const content = replyDraft.value.trim();
	if (!content || replyPending.value) {
		return;
	}

	replyPending.value = true;
	replyError.value = '';

	try {
		await saveGuestbookReply(entry.id, content);
		const now = new Date().toISOString();
		entry.reply = {
			author: member.value?.nickname ?? '',
			content,
			repliedAt: formatSeoulDateTime(now),
			repliedAtIso: now,
		};
		replyTargetId.value = null;
		notifySuccess('답글을 남겼습니다.');
	} catch (error) {
		replyError.value = error?.message ?? '답글을 남기지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
	} finally {
		replyPending.value = false;
	}
}

async function confirmDeleteReply(entry) {
	if (replyPending.value) {
		return;
	}
	replyPending.value = true;
	actionError.value = '';

	try {
		await deleteGuestbookReply(entry.id);
		entry.reply = null;
		deletingReplyId.value = null;
	} catch (error) {
		actionError.value = error?.message ?? '답글을 지우지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
	} finally {
		replyPending.value = false;
	}
}

function canReply(entry) {
	return isAdmin.value && entry.readable && !entry.hiddenByAdmin && !entry.reply;
}

function resetForms() {
	editingId.value = null;
	deletingId.value = null;
	replyTargetId.value = null;
	deletingReplyId.value = null;
	closeReport();
	reportDone.value = '';
	actionError.value = '';
}
</script>

<template>
	<main class="public-shell__main guestbook" :aria-busy="loading">
		<div class="guestbook__inner">
			<header class="guestbook__head">
				<h1 class="guestbook__title">방명록</h1>
				<span v-if="!loaded" class="ui-skeleton guestbook__count-skeleton" aria-hidden="true"></span>
				<p v-else class="guestbook__count">{{ totalElements }}개</p>
				<p class="guestbook__note">로그인한 회원만 남길 수 있고, 비밀글은 쓴 사람과 운영자만 봅니다.</p>
			</header>

			<!-- 로그인 확인이 끝나기 전에는 쓰는 칸 자리만 잡아 둔다. 로그인 단추가 먼저 보였다 바뀌면 누른 사람이 헛걸음한다 -->
			<div v-if="!sessionResolved" class="comments__form-skeleton guestbook__form-skeleton" aria-hidden="true">
				<span class="ui-skeleton ui-skeleton--circle"></span>
				<span class="ui-skeleton"></span>
			</div>
			<!-- 빈 칸을 보여 주고 누른 뒤에 막으면 쓴 글이 날아간다. 처음부터 로그인으로 보낸다 -->
			<div v-else-if="!isSignedIn" class="comment-signin">
				<p class="comment-signin__text">방명록을 남기려면 로그인이 필요합니다.</p>
				<button class="ui-button ui-button--accent" type="button" @click="goToLogin">
					로그인하고 남기기
				</button>
			</div>
			<CommentComposer
				v-else
				v-model="draft"
				:member="member"
				:placeholder="PLACEHOLDER"
				:max-length="maxLength"
				:pending="submitting"
				:error="formError"
				@submit="submit"
				@signout="signOut"
			>
				<template #options>
					<label class="guestbook-secret">
						<input v-model="secret" type="checkbox" />
						<span>비밀글</span>
					</label>
				</template>
			</CommentComposer>

			<p v-if="targetMessage" class="comments__report-done" role="status">{{ targetMessage }}</p>
			<p v-if="actionError" class="comment-reaction__error" role="alert">{{ actionError }}</p>
			<p v-if="reportDone" class="comments__report-done" role="status">{{ reportDone }}</p>

			<div v-if="!loaded && loadError" class="comments__feedback guestbook__feedback">
				<p class="comment-reaction__error" role="alert">{{ loadError }}</p>
				<button type="button" class="ui-button" :disabled="loading" @click="load">다시 시도</button>
			</div>

			<div v-else-if="!loaded" class="guestbook__skeleton" aria-hidden="true">
				<div v-for="index in SKELETON_ROWS" :key="index" class="comments__row-skeleton">
					<span class="ui-skeleton ui-skeleton--circle"></span>
					<div>
						<span class="ui-skeleton"></span>
						<span class="ui-skeleton"></span>
						<span class="ui-skeleton"></span>
					</div>
				</div>
			</div>

			<template v-else>
				<div v-if="loadError" class="comments__feedback guestbook__feedback">
					<p class="comment-reaction__error" role="alert">{{ loadError }}</p>
					<button type="button" class="ui-button" :disabled="loading" @click="load">다시 시도</button>
				</div>

				<p v-if="entries.length === 0 && !loadError" class="guestbook__empty">
					아직 남겨진 글이 없습니다. 첫 인사를 남겨 주세요.
				</p>

				<ul v-else class="comment-list guestbook__list" :class="{ 'guestbook__list--refreshing': loading }">
					<li
						v-for="entry in entries"
						:id="`guestbook-${entry.id}`"
						:key="entry.id"
						class="comment guestbook-entry"
						:class="{ 'guestbook-entry--target': entry.id === targetId }"
					>
						<div class="comment__main">
							<span
								class="comment-avatar"
								:class="{ 'comment-avatar--author': entry.writtenByAdmin }"
								aria-hidden="true"
							>{{ avatarInitial(entry.author) }}</span>

							<div class="comment__content">
								<div class="comment__meta">
									<span class="comment__author">{{ entry.author }}</span>
									<span v-if="entry.writtenByAdmin" class="comment__badge">운영자</span>
									<span v-if="entry.secret" class="guestbook-entry__secret">비밀글</span>
									<time class="comment__time" :datetime="entry.createdAtIso">{{ entry.createdAt }}</time>
									<span v-if="entry.edited" class="comment__edited">수정됨</span>
								</div>

								<!-- 가린 사람에게 "삭제" 라고 하면 자기가 지운 줄 안다. 나에게만 보인다는 것도 같이 말한다 -->
								<p v-if="entry.hiddenByAdmin" class="comment__text comment__text--deleted">
									운영자가 가린 글입니다. 나에게만 보입니다.
								</p>
								<!-- 본문은 서버가 처음부터 비워서 준다. 여기서는 그 자리를 설명만 한다 -->
								<p v-else-if="!entry.readable" class="comment__text comment__text--deleted">
									비밀글입니다. 쓴 사람과 운영자만 볼 수 있습니다.
								</p>
								<CommentEditForm
									v-else-if="editingId === entry.id"
									v-model="editDraft"
									:max-length="maxLength"
									:rows="3"
									label="방명록 고치기"
									:pending="editPending"
									:error="editError"
									@save="saveEdit(entry)"
									@cancel="cancelEdit"
								>
									<template #options>
										<label class="guestbook-secret guestbook-secret--edit">
											<input v-model="editSecret" type="checkbox" />
											<span>비밀글</span>
										</label>
									</template>
								</CommentEditForm>
								<p v-else class="comment__text guestbook-entry__text">{{ entry.content }}</p>

								<CommentActions
									v-if="entry.readable"
									:comment="entry"
									:signed-in="isSignedIn"
									:reactions="false"
									:replyable="canReply(entry)"
									subject="방명록 글"
									:editing="editingId === entry.id"
									:deleting="deletingId === entry.id"
									:delete-pending="deletePending"
									@reply="openReply(entry)"
									@report="openReport(entry)"
									@edit="startEdit(entry)"
									@ask-delete="askDelete(entry)"
									@confirm-delete="confirmDelete(entry)"
									@cancel-delete="cancelDelete"
								/>
							</div>
						</div>

						<div v-if="entry.reply || replyTargetId === entry.id" class="comment__replies">
							<div v-if="entry.reply" class="reply guestbook-reply">
								<span class="comment-avatar comment-avatar--author" aria-hidden="true">
									{{ avatarInitial(entry.reply.author) }}
								</span>
								<div class="comment__content">
									<div class="comment__meta">
										<span class="comment__author">{{ entry.reply.author }}</span>
										<span class="comment__badge">운영자</span>
										<time class="comment__time" :datetime="entry.reply.repliedAtIso">{{ entry.reply.repliedAt }}</time>
									</div>
									<CommentEditForm
										v-if="replyTargetId === entry.id"
										v-model="replyDraft"
										:max-length="maxLength"
										:rows="2"
										label="답글 고치기"
										:pending="replyPending"
										:error="replyError"
										@save="saveReply(entry)"
										@cancel="cancelReply"
									/>
									<p v-else class="comment__text guestbook-entry__text">{{ entry.reply.content }}</p>

									<div v-if="isAdmin && replyTargetId !== entry.id" class="comment__actions">
										<template v-if="deletingReplyId === entry.id">
											<span class="comment__confirm">답글을 지울까요?</span>
											<button
												class="comment__action comment__action--danger"
												type="button"
												:disabled="replyPending"
												@click="confirmDeleteReply(entry)"
											>{{ replyPending ? '지우는 중…' : '지우기' }}</button>
											<button class="comment__action" type="button" @click="deletingReplyId = null">취소</button>
										</template>
										<template v-else>
											<button class="comment__action" type="button" @click="openReply(entry)">수정</button>
											<button class="comment__action" type="button" @click="deletingReplyId = entry.id">삭제</button>
										</template>
									</div>
								</div>
							</div>

							<form
								v-else
								class="reply-form"
								@submit.prevent="saveReply(entry)"
							>
								<label class="reply-form__target" :for="`guestbook-reply-${entry.id}`">
									{{ entry.author }} 님에게 답글 쓰는 중
								</label>
								<textarea
									:id="`guestbook-reply-${entry.id}`"
									v-model="replyDraft"
									class="reply-form__input"
									:maxlength="maxLength"
									rows="2"
								></textarea>
								<div class="reply-form__footer">
									<span class="comment-form__counter">{{ replyDraft.length }} / {{ maxLength }}</span>
									<div class="comment-form__actions">
										<button class="ui-button" type="button" @click="cancelReply">취소</button>
										<button
											class="ui-button ui-button--accent"
											type="submit"
											:disabled="replyPending || replyDraft.trim() === ''"
										>{{ replyPending ? '등록 중' : '답글 등록' }}</button>
									</div>
								</div>
								<p v-if="replyError" class="comment-form__error" role="alert">{{ replyError }}</p>
							</form>
						</div>
					</li>
				</ul>

				<nav v-if="totalPages > 1" class="post-pager" aria-label="방명록 페이지">
					<RouterLink
						v-if="blockStart > 1"
						class="post-pager__button"
						:to="{ query: queryForPage(blockStart - 1) }"
					>‹ 이전 {{ BLOCK_SIZE }}쪽</RouterLink>
					<RouterLink
						v-for="number in pageNumbers"
						:key="number"
						class="post-pager__number"
						:class="{ 'post-pager__number--active': number === page }"
						:aria-current="number === page ? 'page' : undefined"
						:to="{ query: queryForPage(number) }"
					>{{ number }}</RouterLink>
					<RouterLink
						v-if="blockEnd < totalPages"
						class="post-pager__button"
						:to="{ query: queryForPage(blockEnd + 1) }"
					>다음 {{ BLOCK_SIZE }}쪽 ›</RouterLink>
				</nav>
			</template>
		</div>

		<CommentReportDialog
			:open="reportTarget !== null"
			:target="reportTarget?.author ?? ''"
			subject="방명록 글"
			:pending="reportPending"
			:error="reportError"
			@submit="submitReport"
			@close="closeReport"
		/>
	</main>
</template>
