<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';

const props = defineProps({
	section: {
		type: Object,
		required: true,
	},
	topics: {
		type: Array,
		required: true,
	},
	sectionLoading: {
		type: Boolean,
		default: false,
	},
	topicsLoading: {
		type: Boolean,
		default: false,
	},
	sectionError: {
		type: String,
		default: '',
	},
	topicsError: {
		type: String,
		default: '',
	},
	sectionPending: {
		type: Boolean,
		default: false,
	},
	topicsPending: {
		type: Boolean,
		default: false,
	},
});
const emit = defineEmits(['retry-section', 'retry-topics', 'focus-home']);
const sectionElement = ref(null);
const titleElement = ref(null);
const sectionRetryButton = ref(null);
const topicsRetryButton = ref(null);
const isVisible = computed(() => Boolean(props.sectionLoading || props.topicsLoading
	|| props.sectionError || props.topicsError || props.topics.length > 0));
const title = computed(() => (props.section.title || '').trim() || '주제');
const hasSectionContent = computed(() => [props.section.title, props.section.intro, props.section.noteBadge, props.section.note]
	.some(value => value?.trim()));
const sectionErrorMessage = computed(() => props.sectionError === 'refresh' && hasSectionContent.value
	? '주제 소개를 새로 불러오지 못했습니다. 이전에 불러온 소개를 표시합니다.'
	: '주제 소개를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
const topicsErrorMessage = computed(() => props.topicsError === 'refresh' && props.topics.length > 0
	? '주제 목록을 새로 불러오지 못했습니다. 이전에 불러온 주제를 표시합니다.'
	: '주제 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
let active = true;

onBeforeUnmount(() => {
	active = false;
});

function retrySection() {
	if (!props.sectionPending) {
		emit('retry-section');
	}
}

function retryTopics() {
	if (!props.topicsPending) {
		emit('retry-topics');
	}
}

watch([() => props.sectionError, () => props.topicsError], async ([sectionError, topicsError], [previousSectionError, previousTopicsError]) => {
	const wasFocused = (previousSectionError && !sectionError && sectionRetryButton.value === document.activeElement)
		|| (previousTopicsError && !topicsError && topicsRetryButton.value === document.activeElement);
	if (!wasFocused) {
		return;
	}
	await nextTick();
	if (!active || document.activeElement !== document.body) {
		return;
	}
	// 정상 빈 목록이면 제목도 사라지므로 살아 있는 홈 랜드마크에 초점을 남긴다.
	if (titleElement.value) {
		titleElement.value.focus({ preventScroll: true });
	} else {
		emit('focus-home');
	}
});

watch(isVisible, async (visible, previousVisible) => {
	if (visible || !previousVisible) {
		return;
	}
	const focused = document.activeElement;
	if (!sectionElement.value?.contains(focused)) {
		return;
	}
	await nextTick();
	// 소개 복구 뒤 늦은 빈 목록이 제목을 없앨 때도 자기 영역의 초점만 보호한다.
	if (active && !focused.isConnected && document.activeElement === document.body) {
		emit('focus-home');
	}
});
</script>

<template>
	<section
		v-if="isVisible"
		ref="sectionElement"
		class="home-section home-topics"
		:aria-labelledby="sectionLoading ? undefined : 'home-topics-title'"
		:aria-label="sectionLoading ? '주제' : undefined"
		:aria-busy="sectionLoading || topicsLoading || sectionPending || topicsPending"
	>
		<div v-if="sectionLoading" class="home-topics__header" aria-hidden="true">
			<span class="ui-skeleton home-skeleton--section-title"></span>
			<span class="ui-skeleton home-skeleton--section-intro"></span>
		</div>
		<div v-else class="home-topics__header">
			<h2 id="home-topics-title" ref="titleElement" class="home-section__title" tabindex="-1">{{ title }}</h2>
			<p class="home-topics__intro">{{ section.intro }}</p>
		</div>

		<div v-if="sectionError" class="home-section__feedback home-topics__feedback--section" :aria-busy="sectionPending">
			<p role="alert">{{ sectionErrorMessage }}</p>
			<button
				ref="sectionRetryButton"
				type="button"
				class="ui-button home-section__retry"
				aria-label="주제 소개 다시 시도"
				:aria-disabled="sectionPending"
				@click="retrySection"
			>
				{{ sectionPending ? '다시 불러오는 중…' : '다시 시도' }}
			</button>
		</div>
		<div v-if="topicsError" class="home-section__feedback home-topics__feedback--topics" :aria-busy="topicsPending">
			<p role="alert">{{ topicsErrorMessage }}</p>
			<button
				ref="topicsRetryButton"
				type="button"
				class="ui-button home-section__retry"
				aria-label="주제 목록 다시 시도"
				:aria-disabled="topicsPending"
				@click="retryTopics"
			>
				{{ topicsPending ? '다시 불러오는 중…' : '다시 시도' }}
			</button>
		</div>

		<div v-if="topicsLoading" class="home-topic-grid" aria-hidden="true">
			<article v-for="index in 3" :key="index" class="home-topic-card home-topic-card--skeleton">
				<span class="ui-skeleton home-skeleton--topic-label"></span>
				<span class="ui-skeleton home-skeleton--topic-title"></span>
				<span class="ui-skeleton home-skeleton--topic-description"></span>
				<span class="ui-skeleton home-skeleton--topic-keywords"></span>
			</article>
		</div>
		<div v-else class="home-topic-grid">
			<article v-for="topic in topics" :key="topic.id" class="home-topic-card">
				<span class="home-topic-card__label">{{ topic.label }}</span>
				<h3 class="home-topic-card__title">{{ topic.title }}</h3>
				<p class="home-topic-card__description">{{ topic.description }}</p>

				<div v-if="topic.keywords?.length > 0" class="home-topic-card__keywords" aria-label="관련 키워드">
					<span v-for="keyword in topic.keywords" :key="keyword" class="home-topic-card__keyword">
						{{ keyword }}
					</span>
				</div>
			</article>
		</div>

		<div v-if="sectionLoading" class="home-topics__note" aria-hidden="true">
			<span class="ui-skeleton home-skeleton--note-badge"></span>
			<span class="ui-skeleton home-skeleton--note"></span>
		</div>
		<div v-else class="home-topics__note">
			<span class="home-topics__badge">{{ section.noteBadge }}</span>
			<span>{{ section.note }}</span>
		</div>
	</section>
</template>
