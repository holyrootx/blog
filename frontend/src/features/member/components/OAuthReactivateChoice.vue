<script setup>
/**
 * 탈퇴했던 계정으로 다시 들어온 경우의 선택 화면.
 *
 * "삭제" 라고 쓰지 않는다. 새로 만들기를 골라도 예전 댓글은 지워지지 않고 그 자리에
 * 그대로 보인다. 끊어지는 건 "그게 내 것" 이라는 연결뿐이다. 지워진다고 적으면
 * 지워질 거라 기대하고 고르게 되고, 안 지워지면 항의가 온다.
 */
defineProps({
  previousNickname: { type: String, default: '' },
  submitting: { type: Boolean, default: false },
  errorMessage: { type: String, default: '' },
});

const emit = defineEmits(['reactivate', 'rejoin']);
</script>

<template>
  <div class="member-auth__panel">
    <h1 class="member-auth__title">예전에 쓰시던 계정이 있습니다</h1>
    <p class="member-auth__description">
      같은 계정으로 다시 로그인하셨습니다. 어떻게 할지 골라 주세요.
    </p>

    <p v-if="errorMessage" class="member-auth__error" role="alert">{{ errorMessage }}</p>

    <div class="member-auth__choices">
      <button
        class="member-auth__choice"
        type="button"
        :disabled="submitting"
        @click="emit('reactivate')"
      >
        <span class="member-auth__choice-title">예전 계정 쓰기</span>
        <span class="member-auth__choice-detail">
          닉네임 <strong>{{ previousNickname || '(이름 없음)' }}</strong> 과 예전에 쓴 댓글을 되찾습니다.
        </span>
      </button>

      <button
        class="member-auth__choice"
        type="button"
        :disabled="submitting"
        @click="emit('rejoin')"
      >
        <span class="member-auth__choice-title">새로 만들기</span>
        <span class="member-auth__choice-detail">
          빈 상태로 시작합니다. 예전 댓글은 그대로 남지만 내 것이 아니게 됩니다.
          되돌릴 수 없습니다.
        </span>
      </button>
    </div>
  </div>
</template>
