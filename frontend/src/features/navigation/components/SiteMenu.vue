<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';

import { loadCategories } from '../../post/data/categoryStore';

/**
 * 좁은 화면에서 분류로 가는 길.
 *
 * <p>넓은 화면에서는 헤더에 분류가 그대로 늘어서 있어서 이 단추가 필요 없다. 그래서
 * 768px 아래에서만 보인다 — 분류가 숨는 폭과 같은 값이라 둘 중 하나는 늘 보인다.</p>
 *
 * <p>화면을 덮는 창이 아니라 단추 아래 목록으로 연다. 갈 곳이 다섯 개뿐이라 덮개까지
 * 씌울 값어치가 없고, 덮으면 보던 글이 가려져서 "어디로 가지" 를 고르는 동안 맥락을
 * 잃는다. 헤더의 회원 메뉴도 같은 방식이다.</p>
 *
 * <p>약관 같은 것은 담지 않는다. 여기는 글을 찾아가는 자리이고, 그것들은 푸터에 있다.</p>
 */
const route = useRoute();

const open = ref(false);
const root = ref(null);
const categories = ref([]);

function toggle() {
  open.value = !open.value;

  if (!open.value) {
    return;
  }

  // 열 때 받는다. 헤더가 뜨자마자 부르면 아무도 안 열어 볼 메뉴 때문에 요청이 나간다.
  // 이미 받아 둔 값이 있으면 그대로 쓴다
  loadCategories().then((found) => {
    categories.value = found;
  });
}

function close() {
  open.value = false;
}

function closeOnOutside(event) {
  if (open.value && root.value && !root.value.contains(event.target)) {
    close();
  }
}

function closeOnEscape(event) {
  if (event.key === 'Escape') {
    close();
  }
}

onMounted(() => {
  document.addEventListener('click', closeOnOutside);
  document.addEventListener('keydown', closeOnEscape);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', closeOnOutside);
  document.removeEventListener('keydown', closeOnEscape);
});

// 화면을 옮기면 닫는다. 메뉴는 옮기려고 여는 것이라 안 닫으면 새 화면 위에 남는다
watch(() => route.fullPath, close);
</script>

<template>
  <div ref="root" class="site-menu">
    <slot name="trigger" :open="toggle" :expanded="open" />

    <nav v-if="open" class="site-menu__dropdown" aria-label="분류">
      <RouterLink class="site-menu__link" :to="{ name: 'post-list' }">전체 글</RouterLink>

      <p v-if="categories.length > 0" class="site-menu__label">분류</p>
      <RouterLink
        v-for="category in categories"
        :key="category.id"
        class="site-menu__link"
        :to="{ name: 'post-list', query: { category: String(category.id) } }"
      >{{ category.name }}</RouterLink>
    </nav>
  </div>
</template>
