<script setup>
import { formatSeoulDateTime } from '../../../shared/time/seoulTime';

/**
 * 지난번 읽던 곳으로 가는 단추.
 *
 * <p>화면 아래에 띄운다. 본문 위에 끼워 넣으면 자리를 받아 오는 순간 글이 한 줄 밀려서, 읽기 시작한
 * 사람의 눈앞이 흔들린다. 저절로 옮기지 않고 누를 때만 옮긴다.</p>
 */
defineProps({
	savedAt: {
		type: String,
		default: '',
	},
});

const emit = defineEmits(['resume', 'dismiss']);
</script>

<template>
	<div class="post-resume" role="region" aria-label="이어서 읽기">
		<p class="post-resume__text">
			지난번 읽던 곳이 있어요
			<time v-if="savedAt" class="post-resume__time" :datetime="savedAt">{{ formatSeoulDateTime(savedAt) }}</time>
		</p>
		<button class="ui-button ui-button--accent post-resume__action" type="button" @click="emit('resume')">
			이어서 읽기
		</button>
		<button class="post-resume__close" type="button" aria-label="이어서 읽기 닫기" title="닫기" @click="emit('dismiss')">
			×
		</button>
	</div>
</template>
