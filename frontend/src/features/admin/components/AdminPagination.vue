<script setup>
import { computed } from 'vue';

// << < 1 2 3 ... 10 > >>
// 페이지 번호는 blockSize 개씩 묶어서 보여준다.
const props = defineProps({
  page: {
    type: Number,
    required: true,
  },
  totalPages: {
    type: Number,
    required: true,
  },
  blockSize: {
    type: Number,
    default: 10,
  },
});

const emit = defineEmits(['update:page']);

const blockStart = computed(
  () => Math.floor((props.page - 1) / props.blockSize) * props.blockSize + 1,
);

const pageNumbers = computed(() => {
  const end = Math.min(blockStart.value + props.blockSize - 1, props.totalPages);
  const numbers = [];

  for (let number = blockStart.value; number <= end; number += 1) {
    numbers.push(number);
  }

  return numbers;
});

const isFirst = computed(() => props.page <= 1);
const isLast = computed(() => props.page >= props.totalPages);

function move(nextPage) {
  if (nextPage < 1 || nextPage > props.totalPages || nextPage === props.page) {
    return;
  }

  emit('update:page', nextPage);
}
</script>

<template>
  <!-- 1페이지뿐이면 아예 그리지 않는다 -->
  <nav v-if="totalPages > 1" class="admin-pagination" aria-label="페이지 이동">
    <button
      class="admin-pagination__button"
      type="button"
      :disabled="isFirst"
      aria-label="첫 페이지"
      @click="move(1)"
    >
      «
    </button>
    <button
      class="admin-pagination__button"
      type="button"
      :disabled="isFirst"
      aria-label="이전 페이지"
      @click="move(page - 1)"
    >
      ‹
    </button>

    <button
      v-for="number in pageNumbers"
      :key="number"
      class="admin-pagination__button admin-pagination__button--number"
      :class="{ 'admin-pagination__button--active': number === page }"
      type="button"
      :aria-current="number === page ? 'page' : undefined"
      @click="move(number)"
    >
      {{ number }}
    </button>

    <button
      class="admin-pagination__button"
      type="button"
      :disabled="isLast"
      aria-label="다음 페이지"
      @click="move(page + 1)"
    >
      ›
    </button>
    <button
      class="admin-pagination__button"
      type="button"
      :disabled="isLast"
      aria-label="마지막 페이지"
      @click="move(totalPages)"
    >
      »
    </button>
  </nav>
</template>
