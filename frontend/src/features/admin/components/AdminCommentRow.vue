<script setup>
import { canReply, commentState } from '../data/adminCommentRules';

/**
 * 댓글 관리 목록의 한 줄. 댓글을 안 주면 같은 크기의 불러오는 중 자리를 그린다.
 *
 * 줄을 누르면 상세 창이 열린다. 줄 안의 단추와 글 제목 링크는 제 일만 한다.
 */
defineProps({
  comment: {
    type: Object,
    default: null,
  },
});

const emit = defineEmits(['open', 'reply', 'visibility']);

function formatDateTime(value) {
  if (!value) return '-';

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}
</script>

<template>
  <article v-if="!comment" class="admin-comment-row admin-comment-row--skeleton">
    <div class="admin-comment-row__identity">
      <span class="ui-skeleton ui-skeleton--circle admin-comment-row__avatar"></span>
      <div>
        <span class="ui-skeleton"></span>
        <span class="ui-skeleton"></span>
      </div>
    </div>
    <div class="admin-comment-row__body">
      <span class="ui-skeleton"></span>
      <span class="ui-skeleton"></span>
      <span class="ui-skeleton"></span>
    </div>
    <span class="ui-skeleton admin-comment-row__action-skeleton"></span>
  </article>

  <article
    v-else
    class="admin-comment-row admin-comment-row--clickable"
    :class="{ 'admin-comment-row--hidden': comment.hidden }"
    tabindex="0"
    @click="emit('open', comment.id)"
    @keydown.enter.self="emit('open', comment.id)"
  >
    <div class="admin-comment-row__identity">
      <span class="admin-comment-row__avatar" aria-hidden="true">
        {{ Array.from(comment.nickname)[0] ?? '' }}
      </span>
      <div>
        <strong>{{ comment.nickname }}</strong>
        <span>{{ comment.memberRole === 'ADMIN' ? '관리자' : '회원' }}</span>
      </div>
    </div>

    <div class="admin-comment-row__body">
      <div class="admin-comment-row__meta">
        <span
          class="admin-badge"
          :class="comment.hidden || comment.postPurged ? 'admin-badge--off' : 'admin-badge--on'"
        >
          {{ commentState(comment) }}
        </span>
        <!--
          미처리와 전체를 나눠 보여 준다. 전체만 보이면 처리해도 숫자가 그대로라
          무엇을 아직 안 봤는지 알 수 없다
        -->
        <button
          v-if="comment.reportCount > 0"
          class="admin-badge admin-badge--report"
          :class="{ 'admin-badge--report-pending': comment.unhandledReportCount > 0 }"
          type="button"
          @click.stop="emit('open', comment.id)"
        >
          신고 {{ comment.unhandledReportCount > 0
            ? `${comment.unhandledReportCount}건 대기`
            : `${comment.reportCount}건 처리됨` }}
        </button>
        <span>{{ formatDateTime(comment.createdAt) }}</span>
        <span v-if="comment.postPurged">원글 영구 삭제</span>
        <a v-else :href="`/posts/${comment.postId}`" target="_blank" rel="noopener noreferrer" @click.stop>
          {{ comment.postTitle }}
        </a>
      </div>
      <p v-if="comment.contentPurged" class="admin-comment-row__purged">
        보관 기간이 지나 원문을 파기했습니다.
      </p>
      <p v-else>{{ comment.content }}</p>
      <span v-if="comment.parentId !== null" class="admin-comment-row__reply-mark">
        답글 · 원댓글 #{{ comment.parentId }}
      </span>
    </div>

    <div class="admin-comment-row__actions" @click.stop>
      <button
        v-if="canReply(comment)"
        class="admin-button admin-button--small"
        type="button"
        @click="emit('reply', comment)"
      >
        답글
      </button>
      <!-- 원문을 파기한 댓글은 되살릴 내용이 없다. 서버도 거절한다 -->
      <button
        class="admin-button admin-button--small"
        :class="{ 'admin-button--danger': !comment.hidden }"
        type="button"
        :disabled="comment.contentPurged || comment.postPurged"
        :title="comment.postPurged ? '원글이 영구 삭제되어 댓글 상태를 변경할 수 없습니다' : comment.contentPurged ? '원문을 파기해 되살릴 수 없습니다' : undefined"
        @click="emit('visibility', comment)"
      >
        {{ comment.hidden ? '복구' : '숨김' }}
      </button>
    </div>
  </article>
</template>

<style scoped>
.admin-comment-row {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr) auto;
  gap: 20px;
  align-items: start;
  padding: 22px 20px;
  border-bottom: 1px solid var(--admin-list-line);
}

.admin-comment-row:last-child {
  border-bottom: 0;
}

.admin-comment-row--clickable {
  cursor: pointer;
}

.admin-comment-row--clickable:hover {
  background: var(--admin-surface-hover);
}

.admin-comment-row--clickable:focus-visible {
  outline: 2px solid var(--admin-control-accent);
  outline-offset: -2px;
}

.admin-comment-row--hidden {
  background: var(--admin-surface-subtle);
}

.admin-comment-row__identity {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.admin-comment-row__avatar {
  display: grid;
  flex: none;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 50%;
  background: var(--admin-chart-5);
  color: var(--admin-text-strong);
  font-size: 12px;
  font-weight: 800;
}

.admin-comment-row__identity strong,
.admin-comment-row__identity div > span {
  display: block;
}

.admin-comment-row__identity strong {
  overflow: hidden;
  color: var(--admin-text-strong);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-comment-row__identity div > span {
  margin-top: 3px;
  color: var(--admin-meta);
  font-size: 11px;
}

.admin-comment-row__body {
  min-width: 0;
}

.admin-comment-row__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--admin-meta);
  font-size: 11.5px;
}

.admin-comment-row__meta a {
  overflow: hidden;
  color: var(--admin-text);
  text-decoration: none;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-comment-row__meta a:hover {
  color: var(--admin-accent-dark);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.admin-comment-row__body > p {
  margin-top: 10px;
  color: var(--admin-text-strong);
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.admin-comment-row--hidden .admin-comment-row__body > p {
  color: var(--admin-text-muted);
}

.admin-comment-row__body > p.admin-comment-row__purged {
  color: var(--admin-meta);
  font-style: italic;
}

.admin-comment-row__reply-mark {
  display: block;
  margin-top: 8px;
  color: var(--admin-meta);
  font-size: 11px;
}

.admin-comment-row__actions {
  display: flex;
  gap: 6px;
}

/* 불러오는 중 자리. 실제 줄과 같은 칸을 차지해 목록이 들어와도 밀리지 않는다 */
.admin-comment-row--skeleton .admin-comment-row__identity > div,
.admin-comment-row--skeleton .admin-comment-row__body {
  display: grid;
  gap: 9px;
}

.admin-comment-row--skeleton .admin-comment-row__avatar {
  width: 38px;
  height: 38px;
}

.admin-comment-row--skeleton .admin-comment-row__identity > div .ui-skeleton:first-child {
  width: 82px;
  height: 15px;
}

.admin-comment-row--skeleton .admin-comment-row__identity > div .ui-skeleton:last-child {
  width: 52px;
  height: 13px;
}

.admin-comment-row--skeleton .admin-comment-row__body .ui-skeleton:nth-child(1) {
  width: 120px;
  height: 18px;
}

.admin-comment-row--skeleton .admin-comment-row__body .ui-skeleton:nth-child(2) {
  width: min(620px, 90%);
  height: 20px;
}

.admin-comment-row--skeleton .admin-comment-row__body .ui-skeleton:nth-child(3) {
  width: 44%;
  height: 14px;
}

.admin-comment-row__action-skeleton {
  width: 70px;
  height: 32px;
}

@media (max-width: 900px) {
  .admin-comment-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .admin-comment-row__identity {
    grid-column: 1 / -1;
  }
}
</style>
