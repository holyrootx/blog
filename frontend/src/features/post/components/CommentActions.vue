<script setup>
import { computed } from 'vue';
import CommentActionIcon from './CommentActionIcon.vue';

const props = defineProps({
  comment: { type: Object, required: true },
  signedIn: Boolean,
  reply: Boolean,
  editing: Boolean,
  deleting: Boolean,
  reactionPending: Boolean,
  deletePending: Boolean,
});
const emit = defineEmits(['react', 'reply', 'report', 'edit', 'ask-delete', 'confirm-delete', 'cancel-delete']);
const canReport = computed(() => props.signedIn && !props.comment.deleted && !props.comment.mine);
const canManage = computed(() => props.signedIn && !props.comment.deleted && props.comment.mine);
const reported = computed(() => Boolean(props.comment.reportedByMe));
</script>

<template>
  <div class="comment__actions">
    <button
      class="comment__action comment__action--icon"
      :class="{ 'comment__action--active': comment.likedByMe }"
      type="button"
      :disabled="reactionPending"
      :aria-pressed="comment.likedByMe"
      aria-label="좋아요"
      title="좋아요"
      @click="emit('react', 'LIKE')"
    >
      <CommentActionIcon name="like" />
      {{ comment.likeCount }}
    </button>
    <button
      class="comment__action comment__action--icon"
      :class="{ 'comment__action--active': comment.dislikedByMe }"
      type="button"
      :disabled="reactionPending"
      :aria-pressed="comment.dislikedByMe"
      aria-label="싫어요"
      title="싫어요"
      @click="emit('react', 'DISLIKE')"
    >
      <CommentActionIcon name="dislike" />
      {{ comment.dislikeCount }}
    </button>
    <button
      v-if="!reply"
      class="comment__action comment__action--strong"
      type="button"
      @click="emit('reply')"
    >
      답글
    </button>
    <button
      v-if="canReport"
      class="comment__action comment__action--icon comment__action--report"
      :class="{ 'comment__action--reported': reported }"
      type="button"
      :disabled="reported"
      :aria-label="reported ? `이미 신고한 ${reply ? '답글' : '댓글'}` : '신고'"
      :title="reported ? `이미 신고한 ${reply ? '답글' : '댓글'}입니다` : '신고'"
      @click="emit('report')"
    >
      <CommentActionIcon name="report" />
      {{ reported ? '신고됨' : '신고' }}
    </button>

    <template v-if="canManage">
      <button
        v-if="!editing"
        class="comment__action"
        type="button"
        @click="emit('edit')"
      >수정</button>

      <!-- 지우기는 되돌릴 수 없다. 좋아요 옆에 붙어 있어서 한 번 더 묻는다 -->
      <template v-if="deleting">
        <span class="comment__confirm">정말 지울까요?</span>
        <button
          class="comment__action comment__action--danger"
          type="button"
          :disabled="deletePending"
          @click="emit('confirm-delete')"
        >{{ deletePending ? '지우는 중…' : '지우기' }}</button>
        <button class="comment__action" type="button" @click="emit('cancel-delete')">취소</button>
      </template>
      <button v-else class="comment__action" type="button" @click="emit('ask-delete')">
        삭제
      </button>
    </template>
  </div>
</template>
