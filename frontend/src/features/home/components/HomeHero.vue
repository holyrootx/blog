<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';

const props = defineProps({
	hero: {
		type: Object,
		required: true,
	},
	profile: {
		type: Object,
		required: true,
	},
	heroLoading: {
		type: Boolean,
		default: false,
	},
	profileLoading: {
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
});
const emit = defineEmits(['retry', 'focus-home']);
const heading = ref(null);
const retryButton = ref(null);
const title = computed(() => props.hero.title.trim());
const hasHeroContent = computed(() => [props.hero.subTitle, props.hero.title, props.hero.intro, props.hero.heroImageUrl]
	.some(value => value.trim()));
const errorMessage = computed(() => props.error === 'refresh' && hasHeroContent.value
	? '소개를 새로 불러오지 못했습니다. 이전에 불러온 소개를 표시합니다.'
	: '소개를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
let active = true;

onBeforeUnmount(() => {
	active = false;
});

function retry() {
	if (!props.pending) {
		emit('retry');
	}
}

watch(() => props.error, async (error, previousError) => {
	const button = retryButton.value;
	if (error || !previousError || !button || document.activeElement !== button) {
		return;
	}
	await nextTick();
	if (!active || button.isConnected || document.activeElement !== document.body) {
		return;
	}
	if (heading.value) {
		heading.value.focus({ preventScroll: true });
	} else {
		emit('focus-home');
	}
});
</script>

<template>
	<section class="home-hero" :aria-busy="heroLoading && !error">
		<div class="home-hero__copy">
			<div v-if="heroLoading" class="home-hero__copy-skeleton" aria-hidden="true">
				<span class="ui-skeleton home-skeleton--eyebrow"></span>
				<span class="ui-skeleton home-skeleton--headline"></span>
				<span class="ui-skeleton home-skeleton--description"></span>
			</div>
			<template v-else>
				<p v-if="hero.subTitle" class="home-hero__eyebrow">{{ hero.subTitle }}</p>
				<h1 v-if="title" ref="heading" class="home-hero__headline" tabindex="-1">{{ title }}</h1>
				<p v-if="hero.intro" class="home-hero__description">{{ hero.intro }}</p>
			</template>

			<div v-if="error" class="home-section__feedback home-hero__feedback">
				<p role="alert">{{ errorMessage }}</p>
				<button
					ref="retryButton"
					type="button"
					class="ui-button home-section__retry"
					aria-label="소개 다시 시도"
					:aria-disabled="pending"
					@click="retry"
				>
					{{ pending ? '다시 불러오는 중…' : '다시 시도' }}
				</button>
			</div>

			<div class="home-hero__profile" :aria-busy="profileLoading">
				<template v-if="profileLoading">
					<span class="ui-skeleton home-skeleton--avatar" aria-hidden="true"></span>
					<div class="home-skeleton-stack" aria-hidden="true">
						<span class="ui-skeleton home-skeleton--profile-name"></span>
						<span class="ui-skeleton home-skeleton--profile-bio"></span>
						<span class="ui-skeleton home-skeleton--profile-bio-short"></span>
					</div>
				</template>
				<template v-else>
					<img
						v-if="profile.avatarImageUrl"
						class="home-hero__avatar"
						:src="profile.avatarImageUrl"
						:alt="`${profile.name} 프로필`"
						width="96"
						height="96"
						decoding="async"
					/>
					<div>
						<strong class="home-hero__profile-name">{{ profile.name }}</strong>
						<span class="home-hero__profile-label">{{ profile.job }}</span>
						<p class="home-hero__profile-bio">{{ profile.intro }}</p>
					</div>
				</template>
			</div>
		</div>

		<div class="home-hero__media" :class="{ 'is-loading': heroLoading }" aria-hidden="true">
			<img
				v-if="hero.heroImageUrl"
				class="home-hero__image"
				:src="hero.heroImageUrl"
				alt="노트북과 책상 위 장비가 놓인 작업 공간"
				loading="eager"
				fetchpriority="high"
			/>
		</div>
	</section>
</template>
