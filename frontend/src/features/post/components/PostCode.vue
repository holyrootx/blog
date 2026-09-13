<script setup>
import { computed } from 'vue';

import { toCodeTokens } from '../../../shared/post/codeHighlight';

// 공개 화면과 편집 화면이 같은 색칠을 쓴다
const props = defineProps({
  code: {
    type: String,
    default: '',
  },
  language: {
    type: String,
    default: '',
  },
});

const tokens = computed(() => toCodeTokens(props.code, props.language));
</script>

<template>
  <pre class="post-code"><code><span
    v-for="(token, index) in tokens"
    :key="index"
    :class="`post-code__${token.kind}`"
  >{{ token.text }}</span></code></pre>
</template>
