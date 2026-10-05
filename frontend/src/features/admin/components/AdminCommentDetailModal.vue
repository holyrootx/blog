<script setup>
import { computed, ref, watch } from 'vue';

import { dismissAdminCommentReports, getAdminCommentDetail } from '../api/adminApi';
import AdminTextInput from './AdminTextInput.vue';
import { groupReportsByReportedContent, reportedContentNotice } from '../data/commentReportGroups';
import BaseModal from '../../../shared/components/BaseModal.vue';
import { formatExactTime } from '../../../shared/time/relativeTime';
import { notifyError, notifySuccess } from '../../../shared/toast/toastStore';

/**
 * 댓글 관리에서 한 줄을 눌렀을 때 여는 창.
 *
 * 위에서부터 지금 댓글, 신고(신고된 댓글만), 변경 기록 순서다.
 * 신고 배지를 눌러도 이 창이 열린다. 신고 창을 따로 두면 신고된 댓글을 판단할 때
 * 두 창을 오가야 한다.
 */
const props = defineProps({
  commentId: {
    type: Number,
    default: null,
  },
});

const emit = defineEmits(['close', 'hide', 'changed']);

const detail = ref(null);
const loading = ref(false);
const loadError = ref('');
const memo = ref('');
const dismissing = ref(false);

// 창을 닫고 다른 댓글을 열었는데 앞 응답이 늦게 오면 엉뚱한 댓글이 보인다
let requestSeq = 0;

const reported = computed(() => (detail.value?.reports.length ?? 0) > 0);

const states = computed(() => {
  if (!detail.value) return [];

  const list = [];
  if (detail.value.parentId !== null) list.push('답글');
  if (detail.value.hiddenByAdmin) list.push('가림');
  else if (detail.value.deleted) list.push('삭제됨');
  if (detail.value.contentPurged) list.push('원문 파기');
  if (detail.value.postPurged) list.push('원글 영구 삭제');
  if (detail.value.edited) list.push('수정됨');
  return list;
});

const reportGroups = computed(() => groupReportsByReportedContent(detail.value?.reports));

watch(() => props.commentId, (id) => {
  memo.value = '';

  if (id === null) {
    detail.value = null;
    return;
  }

  load();
}, { immediate: true });

async function load() {
  const seq = ++requestSeq;
  const id = props.commentId;

  loading.value = true;
  loadError.value = '';
  detail.value = null;

  try {
    const result = await getAdminCommentDetail(id);
    if (seq === requestSeq) detail.value = result;
  } catch (error) {
    if (seq === requestSeq) loadError.value = error?.message ?? '댓글을 불러오지 못했습니다.';
  } finally {
    if (seq === requestSeq) loading.value = false;
  }
}

function close() {
  if (dismissing.value) return;
  emit('close');
}

function askHide() {
  emit('hide', { id: detail.value.id, hidden: detail.value.deleted });
}

/** 신고를 봤지만 댓글은 그대로 둔다 */
async function dismiss() {
  if (!detail.value || dismissing.value) return;

  dismissing.value = true;

  try {
    await dismissAdminCommentReports(detail.value.id, memo.value || null);
    notifySuccess('신고를 처리했습니다. 댓글은 그대로 둡니다.');
    emit('changed');
    dismissing.value = false;
    emit('close');
  } catch (error) {
    notifyError(error?.message ?? '신고를 처리하지 못했습니다.');
    dismissing.value = false;
  }
}

function isUpdate(item) {
  return item.action === 'UPDATE';
}
</script>

<template>
  <BaseModal
    variant="admin"
    size="large"
    :open="commentId !== null"
    title="댓글 상세"
    :description="reported ? '신고가 쌓여도 댓글이 저절로 숨겨지지는 않습니다. 읽어 보고 정하세요.' : ''"
    @close="close"
  >
    <div :aria-busy="loading">
      <div v-if="loading" class="admin-moderation__skeleton" aria-hidden="true">
        <span class="ui-skeleton"></span>
        <span class="ui-skeleton"></span>
        <span class="ui-skeleton"></span>
        <span class="ui-skeleton"></span>
      </div>

      <div v-else-if="loadError" class="admin-comment-detail__error" role="alert">
        <span>{{ loadError }}</span>
        <button class="admin-button admin-button--small" type="button" @click="load">다시 시도</button>
      </div>

      <template v-else-if="detail">
        <section class="admin-comment-detail__section">
          <h3 class="admin-moderation__title">현재 댓글</h3>
          <div class="admin-comment-detail__meta">
            <strong>{{ detail.nickname }}</strong>
            <span>{{ detail.memberRole === 'ADMIN' ? '관리자' : '회원' }}</span>
            <span v-for="state in states" :key="state" class="admin-badge admin-badge--off">{{ state }}</span>
            <span>{{ formatExactTime(detail.createdAt) }}</span>
            <a v-if="!detail.postPurged" :href="`/posts/${detail.postId}`" target="_blank" rel="noopener noreferrer">
              {{ detail.postTitle }}
            </a>
          </div>
          <p v-if="detail.contentPurged" class="admin-comment-detail__notice">
            보관 기간이 지나 원문을 파기했습니다. 관리자도 확인하거나 되살릴 수 없습니다.
          </p>
          <template v-else>
            <p class="admin-moderation__quote">{{ detail.content }}</p>
            <p v-if="detail.contentRetainedUntil" class="admin-comment-detail__notice">
              {{ detail.postPurged ? '원글이 영구 삭제되어 댓글을 공개하거나 복구할 수 없습니다.' : '글쓴이가 지운 댓글이라 공개 화면에는 보이지 않습니다.' }}
              원문은 {{ formatExactTime(detail.contentRetainedUntil) }}까지 관리자만 볼 수 있고,
              이 시각이 지나면 다음 새벽 정리 때 파기해 되살릴 수 없습니다.
            </p>
          </template>
        </section>

        <section v-if="reported" class="admin-comment-detail__section">
          <h3 class="admin-moderation__title">신고 {{ detail.reports.length }}건</h3>

          <div v-for="(group, index) in reportGroups" :key="index" class="admin-comment-detail__report">
            <div class="admin-comment-detail__label">
              <span>신고 시점 본문</span>
              <span v-if="group.editedAfterReport === true" class="admin-comment-detail__flag">신고 후 수정됨</span>
            </div>
            <p v-if="group.status === 'CONFIRMED'" class="admin-moderation__quote">{{ group.content }}</p>
            <p v-else class="admin-comment-detail__notice">
              {{ reportedContentNotice(group, { contentPurged: detail.contentPurged }) }}
            </p>
            <ul class="admin-moderation__list">
              <li v-for="report in group.reports" :key="report.id">
                <span class="admin-moderation__reason">{{ report.reasonLabel }}</span>
                <span v-if="report.detail" class="admin-moderation__detail">{{ report.detail }}</span>
                <span class="admin-moderation__time">
                  {{ formatExactTime(report.reportedAt) }} · {{ report.handled ? '처리됨' : '대기' }}
                </span>
              </li>
            </ul>
          </div>

          <h3 class="admin-moderation__title">지금까지의 조치</h3>
          <p v-if="detail.moderations.length === 0" class="admin-moderation__empty">아직 없습니다.</p>
          <ul v-else class="admin-moderation__list">
            <li v-for="item in detail.moderations" :key="item.id">
              <span class="admin-moderation__reason">{{ item.actionLabel }}</span>
              <span v-if="item.reason" class="admin-moderation__detail">{{ item.reason }}</span>
              <span class="admin-moderation__time">
                {{ formatExactTime(item.actedAt) }} · {{ item.adminNickname }}
              </span>
            </li>
          </ul>

          <AdminTextInput
            v-model="memo"
            label="메모 (선택)"
            placeholder="판단한 이유를 남겨 두면 다음에 다시 읽지 않아도 됩니다"
            :maxlength="200"
          />
        </section>

        <section class="admin-comment-detail__section">
          <h3 class="admin-moderation__title">변경 기록</h3>
          <p v-if="detail.histories.length === 0" class="admin-moderation__empty">
            남은 변경 기록이 없습니다. 기록은 남긴 때부터 6개월이 지나면 파기하고, 원문을 파기한 댓글은
            기록도 함께 파기합니다. 기록 기능이 생기기 전에 쓴 댓글은 처음부터 기록이 없습니다.
          </p>
          <ul v-else class="admin-moderation__list">
            <li v-for="item in detail.histories" :key="item.id">
              <span class="admin-moderation__reason">{{ item.actionLabel }}</span>
              <span class="admin-moderation__time">{{ formatExactTime(item.occurredAt) }}</span>

              <div v-if="isUpdate(item)" class="admin-comment-detail__change">
                <div>
                  <span>이전</span>
                  <p>{{ item.beforeContent }}</p>
                </div>
                <div>
                  <span>이후</span>
                  <p>{{ item.afterContent }}</p>
                </div>
              </div>
              <span v-else-if="item.afterContent" class="admin-moderation__detail">{{ item.afterContent }}</span>
            </li>
          </ul>
        </section>
      </template>
    </div>

    <template #footer>
      <template v-if="reported">
        <button
          class="admin-button"
          type="button"
          :disabled="dismissing || loading"
          @click="dismiss"
        >
          {{ dismissing ? '처리 중' : '문제 없음' }}
        </button>
        <button
          v-if="!detail.deleted && !detail.postPurged"
          class="admin-button admin-button--danger"
          type="button"
          :disabled="dismissing"
          @click="askHide"
        >
          가리기
        </button>
      </template>
      <button class="admin-button" type="button" :disabled="dismissing" @click="close">닫기</button>
    </template>
  </BaseModal>
</template>

<style scoped>
.admin-comment-detail__section + .admin-comment-detail__section {
  margin-top: 8px;
  padding-top: 4px;
  border-top: 1px solid var(--admin-card-line);
}

.admin-comment-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
  color: var(--admin-meta);
  font-size: 13px;
}

.admin-comment-detail__meta strong {
  color: var(--admin-title);
}

.admin-comment-detail__meta a {
  color: inherit;
  text-decoration: underline;
}

.admin-comment-detail__report + .admin-comment-detail__report {
  margin-top: 14px;
}

.admin-comment-detail__label {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
  color: var(--admin-meta);
  font-size: 12px;
}

/* 신고된 내용이 지금 화면과 다르다는 표시. 누르는 단추가 아니다 */
.admin-comment-detail__flag {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--admin-danger-soft);
  color: var(--admin-danger);
  font-weight: 700;
}

.admin-comment-detail__change {
  display: grid;
  flex: 1 1 100%;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.admin-comment-detail__change div {
  min-width: 0;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--admin-surface-subtle);
}

.admin-comment-detail__change span {
  color: var(--admin-meta);
  font-size: 12px;
}

.admin-comment-detail__change p {
  margin: 4px 0 0;
  color: var(--moderation-text);
  line-height: 1.6;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

/* 원문 대신 보여 주는 안내. 인용처럼 보이면 그게 댓글 내용인 줄 안다 */
.admin-comment-detail__notice {
  margin: 0 0 12px;
  color: var(--admin-meta);
  font-size: 13px;
  line-height: 1.6;
}

.admin-comment-detail__error {
  display: flex;
  gap: 10px;
  align-items: center;
  color: var(--admin-danger);
  font-size: 13px;
}

@media (max-width: 600px) {
  .admin-comment-detail__change {
    grid-template-columns: 1fr;
  }
}
</style>
