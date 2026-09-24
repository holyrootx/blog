<script setup>
import { computed } from 'vue';

import { splitByKeyword } from '../../../shared/search/keywordHighlight';

const props = defineProps({
  text: {
    type: String,
    default: '',
  },
  keyword: {
    type: String,
    default: '',
  },
});

const pieces = computed(() => splitByKeyword(props.text, props.keyword));
</script>

<template>
  <!--
    토막마다 태그를 나눈다. mark 는 "여기가 찾던 곳" 이라는 뜻을 가진 태그라
    화면 낭독기도 그렇게 읽는다 — 색만 칠하는 span 과 다르다
  -->
  <span
    ><template v-for="(piece, index) in pieces" :key="index"
      ><mark v-if="piece.hit" class="keyword-hit">{{ piece.text }}</mark
      ><template v-else>{{ piece.text }}</template></template
    ></span
  >
</template>
