<script setup>
// 작은 폼과 확인창 전용 모달.
//
// ⚠️ 글 편집처럼 "쓰던 내용이 날아가면 곤란한" 화면은 모달로 만들지 않는다.
//    docs/2026-006-admin-screen-spec.md §9 에서 폐기된 결정이다.
//    이유: 검증·보호 로직이 화면과 모달 두 곳으로 갈라지고,
//    ESC·바깥클릭이라는 내용 유실 경로가 새로 생긴다.
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

// 확인창이 폼 모달 위에 겹쳐 뜨는 경우가 있어서 열린 모달을 스택으로 센다.
// 이게 없으면 (1) Esc 가 겹친 모달을 한꺼번에 닫고
// (2) 위 모달만 닫아도 아래가 열려 있는데 배경 스크롤 잠금이 풀린다
import {
  hasNoOpenModal,
  isTopModal,
  popModal,
  pushModal,
} from './adminModalStack';

const props = defineProps({
  open: {
    type: Boolean,
    default: false,
  },
  title: {
    type: String,
    required: true,
  },
  description: {
    type: String,
    default: '',
  },
  // 바깥을 눌렀을 때 닫을지. 입력 중인 폼이면 false 를 준다
  closeOnBackdrop: {
    type: Boolean,
    default: true,
  },
  size: {
    type: String,
    default: 'medium', // small | medium | large
  },
});

const emit = defineEmits(['close']);

const panelRef = ref(null);
// 이 인스턴스를 스택에서 식별할 표식
const modalId = Symbol('admin-modal');

function close() {
  emit('close');
}

function onBackdrop() {
  if (props.closeOnBackdrop) {
    close();
  }
}

function onKeydown(event) {
  if (event.key !== 'Escape' || !props.open) {
    return;
  }

  // 겹쳐 있으면 맨 위 것만 닫는다
  if (!isTopModal(modalId)) {
    return;
  }

  close();
}

function leaveStack() {
  popModal(modalId);

  // 아직 열려 있는 모달이 남았으면 잠금을 유지한다
  if (hasNoOpenModal()) {
    document.body.style.overflow = '';
  }
}

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      pushModal(modalId);

      // 모달 뒤 본문이 스크롤되지 않게
      document.body.style.overflow = 'hidden';
      requestAnimationFrame(() => panelRef.value?.focus());
      return;
    }

    leaveStack();
  },
  // 열린 채로 마운트되는 경우에도 잠가야 한다
  { immediate: true },
);

onMounted(() => document.addEventListener('keydown', onKeydown));

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown);
  leaveStack();
});
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="admin-modal" @click.self="onBackdrop">
      <div
        ref="panelRef"
        class="admin-modal__panel"
        :class="`admin-modal__panel--${size}`"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
        tabindex="-1"
      >
        <header class="admin-modal__header">
          <div>
            <h2 class="admin-modal__title">{{ title }}</h2>
            <p v-if="description" class="admin-modal__description">{{ description }}</p>
          </div>
          <button class="admin-modal__close" type="button" aria-label="닫기" @click="close">
            ×
          </button>
        </header>

        <div class="admin-modal__body">
          <slot />
        </div>

        <footer v-if="$slots.footer" class="admin-modal__footer">
          <slot name="footer" />
        </footer>
      </div>
    </div>
  </Teleport>
</template>
