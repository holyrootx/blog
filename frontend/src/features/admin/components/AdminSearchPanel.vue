<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

// 조회조건이 들어가는 틀. 접힌 상태에서는 "첫 줄"만 보인다.
//
// 조회·초기화 버튼은 이 안에 두지 않는다. 조건 입력(찾기)과
// 조회 실행(행동)은 성격이 달라서, 실행 버튼은 아래 툴바가 맡는다.
// 이 패널 우하단에는 표시 방식을 바꾸는 접기/펼치기만 둔다.
//
// 한 줄에 몇 개가 들어가는지는 CSS(그리드 열 수)가 정한다.
// JS가 breakpoint를 따로 들고 있으면 CSS와 어긋나서,
// 좁은 화면에서 "숨었는데 펼치기 버튼이 없는" 상태가 생긴다.
defineProps({
  collapsible: {
    type: Boolean,
    default: true,
  },
});

const emit = defineEmits(['search']);

const fieldsRef = ref(null);
const fieldCount = ref(0);
const columnCount = ref(1);
const expanded = ref(false);

const hiddenCount = computed(() => Math.max(0, fieldCount.value - columnCount.value));

let resizeObserver = null;
let mutationObserver = null;

function measure() {
  const element = fieldsRef.value;

  if (!element) {
    return;
  }

  fieldCount.value = element.children.length;
  columnCount.value = getComputedStyle(element).gridTemplateColumns.split(' ').length;
}

onMounted(() => {
  measure();

  resizeObserver = new ResizeObserver(measure);
  resizeObserver.observe(fieldsRef.value);

  mutationObserver = new MutationObserver(measure);
  mutationObserver.observe(fieldsRef.value, { childList: true });
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  mutationObserver?.disconnect();
});
</script>

<template>
  <!-- 엔터로도 조회되도록 form 은 유지한다 -->
  <form
    class="admin-search"
    :class="{ 'admin-search--collapsed': collapsible && !expanded }"
    @submit.prevent="emit('search')"
  >
    <div ref="fieldsRef" class="admin-search__fields" :data-visible="columnCount">
      <slot />
    </div>

    <div v-if="collapsible && hiddenCount > 0" class="admin-search__footer">
      <button
        class="admin-search__toggle"
        type="button"
        :aria-expanded="expanded"
        @click="expanded = !expanded"
      >
        {{ expanded ? '접기 ⌃' : `펼치기 ⌄ (+${hiddenCount})` }}
      </button>
    </div>

    <!-- 엔터 조회용. 화면에는 보이지 않는다 -->
    <button class="admin-search__submit" type="submit" tabindex="-1" aria-hidden="true"></button>
  </form>
</template>
