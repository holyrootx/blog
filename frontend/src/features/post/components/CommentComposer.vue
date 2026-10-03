<script setup>
import { computed, ref, watch } from 'vue';

const props = defineProps({
  member: { type: Object, default: null },
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  maxLength: { type: Number, default: 1000 },
  pending: Boolean,
  error: { type: String, default: '' },
});
const emit = defineEmits(['update:modelValue', 'submit', 'signout']);
const commentInput = ref(null);
const avatarImageFailed = ref(false);
const showAvatarImage = computed(() => Boolean(props.member?.profileImageUrl) && !avatarImageFailed.value);
const writingAs = computed(() => props.member?.nickname
  ? `${props.member.nickname} 님으로 작성됩니다.`
  : '로그인한 계정으로 작성됩니다.');
watch(() => props.member?.profileImageUrl, () => { avatarImageFailed.value = false; });
function avatarInitial(name) {
  return Array.from(name ?? '')[0] ?? '';
}
defineExpose({ focus: (options) => commentInput.value?.focus(options) });
</script>

<template>
  <form class="comment-form" @submit.prevent="emit('submit')">
    <div class="comment-form__body">
      <!-- 프사가 있으면 그림, 없거나 못 받아오면 첫 글자.
           카카오·네이버는 프사가 선택 동의라 주소가 안 오는 경우도 정상이다 -->
      <img
        v-if="showAvatarImage"
        class="comment-avatar comment-avatar--photo"
        :src="member.profileImageUrl"
        alt=""
        referrerpolicy="no-referrer"
        @error="avatarImageFailed = true"
      />
      <span v-else class="comment-avatar">{{ avatarInitial(member?.nickname) }}</span>
      <div class="comment-form__content">
        <textarea
          ref="commentInput"
          :value="modelValue"
          class="comment-form__input"
          :placeholder="placeholder"
          :maxlength="maxLength"
          rows="2"
          @input="emit('update:modelValue', $event.target.value)"
        ></textarea>
      </div>
    </div>
    <div class="comment-form__footer">
      <span class="comment-form__note">
        {{ writingAs }}
        <button class="comment-form__signout" type="button" @click="emit('signout')">로그아웃</button>
      </span>
      <div class="comment-form__actions">
        <span class="comment-form__counter">{{ modelValue.length }} / {{ maxLength }}</span>
        <button
          class="ui-button ui-button--accent"
          type="submit"
          :disabled="pending"
        >
          {{ pending ? '등록 중' : '등록' }}
        </button>
      </div>
    </div>
    <p v-if="error" class="comment-form__error" role="alert">{{ error }}</p>
  </form>
</template>

<style scoped>
.comment-form__footer {
  flex-wrap: wrap;
  gap: 12px;
}

.comment-form__note {
  min-width: 0;
  overflow-wrap: anywhere;
}

.comment-form__actions {
  flex-shrink: 0;
  margin-left: auto;
  white-space: nowrap;
}
</style>
