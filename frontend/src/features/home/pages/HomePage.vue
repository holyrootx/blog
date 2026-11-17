<script setup>
import { computed, onMounted, reactive, ref } from 'vue';

import HomeHero from '../components/HomeHero.vue';
import PostSection from '../components/PostSection.vue';
import HomeTopicSection from '../components/HomeTopicSection.vue';
import {
	getHomePageHero,
	getHomePosts,
	getHomeTopics,
	getHomeTopicSection,
} from '../api/homeApi';
import { blogProfile, blogProfileLoading } from '../data/blogProfileStore';
import { useSectionLoader } from '../../../shared/composables/useSectionLoader';
import { mergeDefined } from '../../../shared/data/mergeDefined';

// API 응답이 오기 전까지의 초기 상태. mergeDefined가 빈 값을 덮어쓰지 않으므로
// 여기 남은 값은 API가 해당 필드를 내려주지 않았을 때 그대로 노출된다.
const EMPTY_HERO = {
	subTitle: '',
	title: '',
	intro: '',
	heroImageUrl: '',
};

const EMPTY_PROFILE = {
	name: '',
	intro: '',
	job: '',
	avatarImageUrl: '',
	githubUrl: '',
	email: '',
};

const EMPTY_TOPIC_SECTION = {
	title: '',
	intro: '',
	noteBadge: '',
	note: '',
};

const home = reactive({
	hero: { ...EMPTY_HERO },
	topicSection: { ...EMPTY_TOPIC_SECTION },
	topics: [],
	featuredPosts: [],
	recentPosts: [],
});

// 프로필은 헤더와 같이 쓰는 값이라 화면이 따로 받지 않는다. 받는 일은 PublicLayout 이 한다
const profile = computed(() => mergeDefined(EMPTY_PROFILE, blogProfile.value));

const { loading, load } = useSectionLoader(['hero', 'topicSection', 'topics', 'featuredPosts', 'recentPosts']);
const mainElement = ref(null);
const topicErrors = reactive({ topicSection: '', topics: '' });
const topicPending = reactive({ topicSection: false, topics: false });
const topicQueries = {
	topicSection: {
		cacheKey: 'home:topic-section',
		request: getHomeTopicSection,
		apply: (section) => {
			home.topicSection = mergeDefined(EMPTY_TOPIC_SECTION, section);
		},
	},
	topics: {
		cacheKey: 'home:topics',
		request: getHomeTopics,
		apply: (topics) => {
			home.topics = Array.isArray(topics) ? topics : [];
		},
	},
};

async function loadTopic(key) {
	if (topicPending[key]) {
		return;
	}
	topicPending[key] = true;
	let failed = false;
	try {
		await load(key, {
			...topicQueries[key],
			onError: (_error, { showingCached }) => {
				failed = true;
				topicErrors[key] = showingCached ? 'refresh' : 'load';
			},
		});
		if (!failed) {
			topicErrors[key] = '';
		}
	} finally {
		topicPending[key] = false;
	}
}

function focusHome() {
	if (document.activeElement === document.body) {
		mainElement.value?.focus({ preventScroll: true });
	}
}

const postErrors = reactive({ featuredPosts: '', recentPosts: '' });
const postPending = reactive({ featuredPosts: false, recentPosts: false });
const postQueries = {
	featuredPosts: { sort: 'popular', cacheKey: 'home:popular-posts' },
	recentPosts: { sort: 'latest', cacheKey: 'home:latest-posts' },
};

async function loadPosts(key) {
	if (postPending[key]) {
		return;
	}
	postPending[key] = true;
	let failed = false;
	const { sort, cacheKey } = postQueries[key];
	try {
		await load(key, {
			cacheKey,
			request: () => getHomePosts(sort),
			apply: (posts) => {
				home[key] = posts;
			},
			onError: (_error, { showingCached }) => {
				failed = true;
				postErrors[key] = showingCached ? 'refresh' : 'load';
			},
		});
		if (!failed) {
			postErrors[key] = '';
		}
	} finally {
		postPending[key] = false;
	}
}

onMounted(() => {
	load('hero', {
		cacheKey: 'home:hero',
		request: getHomePageHero,
		apply: (hero) => {
			home.hero = mergeDefined(EMPTY_HERO, hero);
		},
	});
	loadTopic('topicSection');
	loadTopic('topics');
	loadPosts('featuredPosts');
	loadPosts('recentPosts');
});
</script>

<template>
	<main ref="mainElement" class="public-shell__main" aria-label="블로그 홈" tabindex="-1">
		<HomeHero
			:hero="home.hero"
			:profile="profile"
			:hero-loading="loading.hero"
			:profile-loading="blogProfileLoading"
		/>
		<HomeTopicSection
			:section="home.topicSection"
			:topics="home.topics"
			:section-loading="loading.topicSection"
			:topics-loading="loading.topics"
			:section-error="topicErrors.topicSection"
			:topics-error="topicErrors.topics"
			:section-pending="topicPending.topicSection"
			:topics-pending="topicPending.topics"
			@retry-section="loadTopic('topicSection')"
			@retry-topics="loadTopic('topics')"
			@focus-home="focusHome"
		/>
		<PostSection
			title="인기글"
			sort="popular"
			:posts="home.featuredPosts"
			:loading="loading.featuredPosts"
			:error="postErrors.featuredPosts"
			:pending="postPending.featuredPosts"
			@retry="loadPosts('featuredPosts')"
		/>
		<PostSection
			title="최근 글"
			sort="latest"
			:posts="home.recentPosts"
			:loading="loading.recentPosts"
			:error="postErrors.recentPosts"
			:pending="postPending.recentPosts"
			@retry="loadPosts('recentPosts')"
		/>
	</main>
</template>
