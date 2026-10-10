<script setup>
import PostInline from './PostInline.vue';

/**
 * 목록 하나를 그린다. 항목 아래의 하위 목록은 이 컴포넌트가 자기 자신을 다시 불러 그린다.
 *
 * 할 일 항목은 체크 상자를 읽기 전용으로 보여 준다. 공개 글을 읽는 사람이 눌러 바꾸는 것이 아니다.
 * 깊이마다 글머리 모양(●○■)과 번호 모양(1. a. i.)을 바꾼다 — 노션과 같다.
 */
defineOptions({ name: 'PostList' });

defineProps({
  list: {
    type: Object,
    required: true,
  },
  depth: {
    type: Number,
    default: 0,
  },
});
</script>

<template>
  <component
    :is="list.ordered ? 'ol' : 'ul'"
    class="post-body__list"
    :class="[
      list.ordered ? 'post-body__list--ordered' : 'post-body__list--bullet',
      `post-body__list--depth-${depth % 3}`,
      { 'post-body__list--todo': !list.ordered && list.items.some((item) => item.checked !== null) },
    ]"
  >
    <li
      v-for="(item, index) in list.items"
      :key="index"
      :class="{
        'post-body__todo': item.checked !== null,
        'post-body__todo--done': item.checked === true,
      }"
    >
      <template v-if="item.checked !== null">
        <!-- 읽기 전용 표시다. 브라우저의 비활성 체크 상자는 회색으로 흐려져 완료 여부가 잘 안 보인다 -->
        <span
          class="post-body__todo-box"
          :class="{ 'post-body__todo-box--done': item.checked }"
          role="img"
          :aria-label="item.checked ? '완료한 일' : '할 일'"
        ><span v-if="item.checked" aria-hidden="true">✓</span></span>
        <span class="post-body__todo-text"><PostInline :tokens="item.inline" /></span>
      </template>
      <PostInline v-else :tokens="item.inline" />

      <PostList v-for="(child, childIndex) in item.children" :key="childIndex" :list="child" :depth="depth + 1" />
    </li>
  </component>
</template>
