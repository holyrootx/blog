<script setup>
// 인라인 서식 토큰을 그린다.
// HTML 문자열을 v-html 로 꽂지 않고 토큰마다 태그를 만들어 Vue 가 이스케이프하게 둔다.
defineProps({
  tokens: {
    type: Array,
    default: () => [],
  },
});
</script>

<template>
  <template v-for="(token, index) in tokens" :key="index">
    <strong v-if="token.type === 'bold'">{{ token.text }}</strong>
    <em v-else-if="token.type === 'italic'">{{ token.text }}</em>
    <code v-else-if="token.type === 'code'" class="post-body__inline-code">{{ token.text }}</code>
    <!-- 본문 링크는 외부로 나가는 경우가 많아 새 탭으로 열고 referrer 를 넘기지 않는다 -->
    <a
      v-else-if="token.type === 'link'"
      class="post-body__link"
      :href="token.href"
      target="_blank"
      rel="noopener noreferrer"
    >{{ token.text }}</a>
    <template v-else>{{ token.text }}</template>
  </template>
</template>
