<script setup>
// 조회조건과 그리드 사이의 한 줄.
// 왼쪽은 "찾은 결과"(건수·개수 선택), 오른쪽은 "결과에 대한 행동"(조회·초기화·등록 등).
defineProps({
  totalCount: {
    type: Number,
    default: null,
  },
});
</script>

<template>
  <div class="admin-toolbar">
    <div class="admin-toolbar__left">
      <span v-if="totalCount !== null" class="admin-toolbar__total">
        전체 <strong>{{ totalCount.toLocaleString('ko-KR') }}</strong>건
      </span>
      <slot name="left" />
    </div>

    <div class="admin-toolbar__right">
      <slot name="right" />
    </div>
  </div>
</template>
