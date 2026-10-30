<script setup>
import { computed, ref, watch } from 'vue';

import {
	deleteAdminGuestbookReply,
	dismissAdminGuestbookReports,
	getAdminGuestbookDetail,
	saveAdminGuestbookReply,
	updateAdminGuestbookVisibility,
} from '../api/adminApi';
import BaseModal from '../../../shared/components/BaseModal.vue';
import { formatExactTime } from '../../../shared/time/relativeTime';

/**
 * 방명록 글 상세. 신고 보기, 답글, 가리기·되돌리기를 한 창에서 한다.
 *
 * <p>댓글과 달리 답글 창을 따로 띄우지 않는다. 답글은 글마다 하나뿐이라 고치기와 달기가
 * 같은 칸이고, 글을 읽으면서 바로 답하는 일이 대부분이다.</p>
 *
 * <p>무엇을 바꿨든 끝나면 {@code changed} 를 알린다. 목록을 다시 읽는 일은 부른 쪽이 한다.</p>
 */
const MAX_LENGTH = 500;

const props = defineProps({
	entryId: {
		type: Number,
		default: null,
	},
});

const emit = defineEmits(['close', 'changed']);

const detail = ref(null);
const loading = ref(false);
const loadError = ref('');
const replyDraft = ref('');
const pending = ref(false);
const actionError = ref('');
let requestId = 0;

const entry = computed(() => detail.value?.entry ?? null);
const pendingReports = computed(() => detail.value?.reports.filter((report) => !report.handled).length ?? 0);
const canReply = computed(() => entry.value && !entry.value.deleted && !entry.value.contentPurged);
const states = computed(() => {
	if (!entry.value) {
		return [];
	}
	const result = [];
	if (entry.value.secret) {
		result.push('비밀글');
	}
	if (entry.value.edited) {
		result.push('수정됨');
	}
	if (entry.value.deletedByWriter) {
		result.push('지움');
	} else if (entry.value.hiddenByAdmin) {
		result.push('가림');
	}
	return result;
});

watch(() => props.entryId, (id) => {
	detail.value = null;
	actionError.value = '';
	if (id !== null) {
		load();
	}
}, { immediate: true });

async function load() {
	const current = ++requestId;
	loading.value = true;
	loadError.value = '';

	try {
		const result = await getAdminGuestbookDetail(props.entryId);
		if (current !== requestId) {
			return;
		}
		detail.value = result;
		replyDraft.value = result.entry.replyContent ?? '';
	} catch (error) {
		if (current !== requestId) {
			return;
		}
		loadError.value = error?.message ?? '방명록 글을 불러오지 못했습니다.';
	} finally {
		if (current === requestId) {
			loading.value = false;
		}
	}
}

async function run(action, message) {
	if (pending.value) {
		return;
	}
	pending.value = true;
	actionError.value = '';

	try {
		await action();
		emit('changed', message);
		await load();
	} catch (error) {
		actionError.value = error?.message ?? '처리하지 못했습니다. 잠시 뒤에 다시 시도해 주세요.';
	} finally {
		pending.value = false;
	}
}

function saveReply() {
	const content = replyDraft.value.trim();
	if (!content) {
		actionError.value = '답글 내용을 입력해 주세요.';
		return;
	}
	run(() => saveAdminGuestbookReply(props.entryId, content), '답글을 저장했습니다.');
}

function removeReply() {
	run(() => deleteAdminGuestbookReply(props.entryId), '답글을 지웠습니다.');
}

function toggleHidden() {
	const hidden = !entry.value.deleted;
	run(
		() => updateAdminGuestbookVisibility(props.entryId, hidden),
		hidden ? '방명록 글을 가렸습니다.' : '방명록 글을 다시 공개했습니다.',
	);
}

function dismiss() {
	run(() => dismissAdminGuestbookReports(props.entryId), '신고를 문제 없음으로 처리했습니다.');
}

function close() {
	if (pending.value) {
		return;
	}
	emit('close');
}
</script>

<template>
	<BaseModal
		variant="admin"
		size="large"
		:open="entryId !== null"
		title="방명록 글 상세"
		:description="pendingReports > 0 ? '신고가 쌓여도 글이 저절로 가려지지는 않습니다. 읽어 보고 정하세요.' : ''"
		:close-on-backdrop="false"
		@close="close"
	>
		<div :aria-busy="loading">
			<div v-if="loading && !detail" class="admin-moderation__skeleton" aria-hidden="true">
				<span class="ui-skeleton"></span>
				<span class="ui-skeleton"></span>
				<span class="ui-skeleton"></span>
				<span class="ui-skeleton"></span>
			</div>

			<div v-else-if="loadError && !detail" class="admin-guestbook-detail__error" role="alert">
				<span>{{ loadError }}</span>
				<button class="admin-button admin-button--small" type="button" @click="load">다시 시도</button>
			</div>

			<template v-else-if="entry">
				<section class="admin-guestbook-detail__section">
					<h3 class="admin-moderation__title">방명록 글</h3>
					<div class="admin-guestbook-detail__meta">
						<strong>{{ entry.nickname }}</strong>
						<span>{{ entry.writtenByAdmin ? '관리자' : '회원' }}</span>
						<span v-for="state in states" :key="state" class="admin-badge admin-badge--off">{{ state }}</span>
						<span>{{ formatExactTime(entry.createdAt) }}</span>
					</div>
					<p v-if="entry.contentPurged" class="admin-guestbook-detail__notice">
						보관 기간이 지나 원문을 파기했습니다. 관리자도 확인하거나 되살릴 수 없습니다.
					</p>
					<template v-else>
						<p class="admin-moderation__quote admin-guestbook-detail__text">{{ entry.content }}</p>
						<p v-if="detail.retainedUntil" class="admin-guestbook-detail__notice">
							쓴 사람이 지운 글이라 공개 화면에 보이지 않고 되살릴 수 없습니다. 원문은
							{{ formatExactTime(detail.retainedUntil) }}까지 관리자만 볼 수 있고, 그 뒤 새벽 정리 때 파기합니다.
						</p>
					</template>
				</section>

				<section v-if="detail.reports.length > 0" class="admin-guestbook-detail__section">
					<h3 class="admin-moderation__title">신고 {{ detail.reports.length }}건</h3>
					<ul class="admin-moderation__list">
						<li v-for="report in detail.reports" :key="report.id">
							<span class="admin-moderation__reason">{{ report.reasonLabel }}</span>
							<span class="admin-moderation__time">
								{{ formatExactTime(report.reportedAt) }} · {{ report.handled ? '처리됨' : '대기' }}
							</span>
							<span v-if="report.detail" class="admin-moderation__detail">{{ report.detail }}</span>
							<!-- 신고 뒤에 고칠 수 있어서 지금 본문이 아니라 신고 당시 본문을 보인다 -->
							<span class="admin-moderation__detail">
								신고 당시 본문:
								{{ report.reportedContent ?? '보관 기간이 지나 파기했습니다.' }}
							</span>
						</li>
					</ul>
				</section>

				<section v-if="canReply" class="admin-guestbook-detail__section">
					<h3 class="admin-moderation__title">운영자 답글</h3>
					<label class="admin-guestbook-detail__label" for="admin-guestbook-reply">
						{{ entry.replyContent ? '답글 고치기' : '답글 달기' }}
						<span v-if="entry.secret">· 비밀글이라 답글도 쓴 사람과 운영자만 봅니다</span>
					</label>
					<textarea
						id="admin-guestbook-reply"
						v-model="replyDraft"
						class="admin-guestbook-detail__reply"
						:maxlength="MAX_LENGTH"
						rows="4"
						placeholder="답글을 입력하세요"
					></textarea>
					<div class="admin-guestbook-detail__reply-meta">
						<span>{{ replyDraft.length }} / {{ MAX_LENGTH }}</span>
						<span v-if="entry.repliedAt">마지막 답글 {{ formatExactTime(entry.repliedAt) }}</span>
					</div>
					<div class="admin-guestbook-detail__reply-actions">
						<button
							v-if="entry.replyContent"
							class="admin-button admin-button--small"
							type="button"
							:disabled="pending"
							@click="removeReply"
						>답글 지우기</button>
						<button
							class="admin-button admin-button--small admin-button--solid"
							type="button"
							:disabled="pending || replyDraft.trim() === ''"
							@click="saveReply"
						>{{ pending ? '저장 중' : '답글 저장' }}</button>
					</div>
				</section>
				<p v-else-if="entry.replyContent" class="admin-guestbook-detail__notice">
					운영자 답글: {{ entry.replyContent }}
				</p>

				<p v-if="actionError" class="admin-form-error" role="alert">{{ actionError }}</p>
			</template>
		</div>

		<!-- 닫기가 가장 오른쪽. 하려던 일이 먼저 오고 그만두는 길이 끝에 온다 -->
		<template #footer>
			<button
				v-if="pendingReports > 0"
				class="admin-button"
				type="button"
				:disabled="pending || loading"
				@click="dismiss"
			>문제 없음</button>
			<!-- 되돌리기는 운영자가 가린 글에만 둔다. 쓴 사람이 지운 글은 되살리지 않는다(서버도 거절한다) -->
			<button
				v-if="entry && !entry.contentPurged && (!entry.deleted || !entry.deletedByWriter)"
				class="admin-button"
				:class="entry.deleted ? 'admin-button--solid' : 'admin-button--danger'"
				type="button"
				:disabled="pending || loading"
				@click="toggleHidden"
			>{{ entry.deleted ? '다시 공개' : '가리기' }}</button>
			<button class="admin-button" type="button" :disabled="pending" @click="close">닫기</button>
		</template>
	</BaseModal>
</template>

<style scoped>
.admin-guestbook-detail__section + .admin-guestbook-detail__section {
	margin-top: 8px;
	padding-top: 4px;
	border-top: 1px solid var(--admin-card-line);
}

.admin-guestbook-detail__meta {
	display: flex;
	flex-wrap: wrap;
	gap: 8px;
	align-items: center;
	margin-bottom: 10px;
	color: var(--admin-meta);
	font-size: 13px;
}

.admin-guestbook-detail__meta strong {
	color: var(--admin-title);
}

.admin-guestbook-detail__text {
	white-space: pre-line;
	overflow-wrap: anywhere;
}

.admin-guestbook-detail__notice {
	margin: 0 0 12px;
	color: var(--admin-meta);
	font-size: 12.5px;
	line-height: 1.6;
}

.admin-guestbook-detail__error {
	display: flex;
	gap: 12px;
	align-items: center;
	color: var(--admin-error-text);
	font-size: 13px;
}

.admin-guestbook-detail__label {
	display: block;
	margin-bottom: 7px;
	color: var(--admin-text-strong);
	font-size: 12px;
	font-weight: 700;
}

.admin-guestbook-detail__label span {
	color: var(--admin-meta);
	font-weight: 500;
}

.admin-guestbook-detail__reply {
	width: 100%;
	padding: 12px;
	border: 1px solid var(--admin-card-line);
	border-radius: 7px;
	background: var(--admin-card);
	color: var(--admin-text-strong);
	font: inherit;
	font-size: 14px;
	line-height: 1.65;
	resize: vertical;
}

.admin-guestbook-detail__reply:focus {
	border-color: var(--admin-accent);
	outline: none;
}

.admin-guestbook-detail__reply-meta {
	display: flex;
	justify-content: space-between;
	gap: 12px;
	margin-top: 7px;
	color: var(--admin-meta);
	font-size: 11.5px;
}

.admin-guestbook-detail__reply-actions {
	display: flex;
	justify-content: flex-end;
	gap: 8px;
	margin-top: 10px;
}
</style>
