<script setup>
import { computed, watch } from 'vue';

import { useMemberAuth } from '../data/memberAuthStore';
import { requestViewedPosts, viewedAtOf } from '../data/viewedPostStore';
import { formatSeoulDateTime } from '../../../shared/time/seoulTime';

/**
 * 글 카드 대표 이미지 위의 "최근 본 글".
 *
 * <p>이미지 위에 겹쳐 띄운다. 제목 줄에 붙이면 답이 도착하는 순간 글줄이 밀리거나 줄바꿈이 바뀐다.</p>
 *
 * <p>"다 읽음" 이라고 하지 않는다. 연 적이 있다는 것만 알지, 끝까지 읽었는지는 모른다.</p>
 */
const props = defineProps({
  postId: {
    type: [Number, String],
    required: true,
  },
});

const { member } = useMemberAuth();

watch(
  () => [member.value?.id ?? null, Number(props.postId)],
  ([memberId, postId]) => requestViewedPosts(memberId, [postId]),
  { immediate: true },
);

const lastViewedAt = computed(() => (member.value ? viewedAtOf(props.postId) : null));
</script>

<template>
  <span
    v-if="lastViewedAt"
    class="post-card__viewed"
    :title="`${formatSeoulDateTime(lastViewedAt)}에 봤습니다`"
  >최근 본 글</span>
</template>
