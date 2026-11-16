<script setup>
import { computed, nextTick, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';

import ViewedPostBadge from '../../member/components/ViewedPostBadge.vue';

const props = defineProps({
	title: {
		type: String,
		required: true,
	},
	posts: {
		type: Array,
		required: true,
	},
	loading: {
		type: Boolean,
		default: false,
	},
	error: {
		type: String,
		default: '',
	},
	pending: {
		type: Boolean,
		default: false,
	},
	/**
	 * 이 묶음을 고른 순서. 목록 화면의 sort 값과 같은 말을 쓴다.
	 *
	 * "전체 보기" 가 이걸 그대로 들고 가야 한다. 안 들고 가면 인기글에서 눌렀는데
	 * 최신순 목록이 열려서, 방금 보던 넉 장이 첫 화면에 없다.
	 */
	sort: {
		type: String,
		default: 'latest',
		validator: (value) => value === 'latest' || value === 'popular',
	},
});
const emit = defineEmits(['retry']);
const titleElement = ref(null);
const retryButton = ref(null);
const titleId = computed(() => `home-posts-${props.sort}-title`);
const errorMessage = computed(() => props.error === 'refresh'
	? '글 목록을 새로 불러오지 못했습니다. 이전에 불러온 결과를 표시합니다.'
	: '글 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');

function retry() {
	if (!props.pending) {
		emit('retry');
	}
}

watch(() => props.error, async (error, previousError) => {
	if (error || !previousError) {
		return;
	}
	// 성공으로 단추가 없어질 때, 그 단추를 보던 사람의 초점만 같은 영역에 남긴다.
	const wasFocused = retryButton.value === document.activeElement;
	await nextTick();
	if (wasFocused && document.activeElement === document.body) {
		titleElement.value?.focus({ preventScroll: true });
	}
});

// 최신순은 목록의 기본값이라 주소에서 뺀다. /posts?sort=latest 보다 /posts 가 낫다
const moreQuery = computed(() => (props.sort === 'popular' ? { sort: 'popular' } : {}));
</script>

<template>
	<section class="home-section" :aria-labelledby="titleId" :aria-busy="loading || pending">
		<div class="home-section__header">
			<h2 :id="titleId" ref="titleElement" class="home-section__title" tabindex="-1">{{ title }}</h2>
			<RouterLink class="home-section__more" :to="{ name: 'post-list', query: moreQuery }">
				전체 보기
			</RouterLink>
		</div>

		<div v-if="error" class="home-section__feedback">
			<p role="alert">{{ errorMessage }}</p>
			<button
				ref="retryButton"
				type="button"
				class="ui-button home-section__retry"
				:aria-disabled="pending"
				@click="retry"
			>
				{{ pending ? '다시 불러오는 중…' : '다시 시도' }}
			</button>
		</div>
		<div v-if="loading" class="post-grid" aria-hidden="true">
			<article v-for="index in 4" :key="index" class="post-card post-card--skeleton">
				<div class="post-card__image ui-skeleton"></div>
				<div class="post-card__body home-skeleton-stack">
					<span class="ui-skeleton home-skeleton--post-category"></span>
					<span class="ui-skeleton home-skeleton--post-title"></span>
					<span class="ui-skeleton home-skeleton--post-meta"></span>
				</div>
			</article>
		</div>
		<div v-else-if="posts.length" class="post-grid">
			<RouterLink
				v-for="post in posts"
				:key="post.id"
				class="post-card"
				:to="{ name: 'post-detail', params: { id: post.id } }"
			>
				<img
					class="post-card__image"
					:src="post.imageUrl"
					:alt="post.title"
					loading="lazy"
					decoding="async"
					fetchpriority="low"
				/>
				<div class="post-card__body">
					<span class="post-card__category">{{ post.category }}</span>
					<h3 class="post-card__title">{{ post.title }}</h3>
					<div class="post-card__meta">
						<time>{{ post.publishedAt }}</time>
						<span>조회 {{ post.views }}</span>
					</div>
				</div>
				<ViewedPostBadge :post-id="post.id" />
			</RouterLink>
		</div>
		<p v-else-if="!error" class="home-section__empty">아직 표시할 글이 없습니다.</p>
	</section>
</template>
