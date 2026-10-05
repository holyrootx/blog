<script setup>
import { computed, onMounted, reactive } from 'vue';

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

onMounted(() => {
  load('hero', {
    cacheKey: 'home:hero',
    request: getHomePageHero,
    apply: (hero) => {
      home.hero = mergeDefined(EMPTY_HERO, hero);
    },
  });
  load('topicSection', {
    cacheKey: 'home:topic-section',
    request: getHomeTopicSection,
    apply: (section) => {
      home.topicSection = mergeDefined(EMPTY_TOPIC_SECTION, section);
    },
  });
  load('topics', {
    cacheKey: 'home:topics',
    request: getHomeTopics,
    apply: (topics) => {
      home.topics = Array.isArray(topics) ? topics : [];
    },
  });
  load('featuredPosts', {
    cacheKey: 'home:popular-posts',
    request: () => getHomePosts('popular'),
    apply: (posts) => {
      home.featuredPosts = posts;
    },
  });
  load('recentPosts', {
    cacheKey: 'home:latest-posts',
    request: () => getHomePosts('latest'),
    apply: (posts) => {
      home.recentPosts = posts;
    },
  });
});
</script>

<template>
  <main class="public-shell__main">
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
    />
    <PostSection
      title="인기글"
      sort="popular"
      :posts="home.featuredPosts"
      :loading="loading.featuredPosts"
    />
    <PostSection
      title="최근 글"
      sort="latest"
      :posts="home.recentPosts"
      :loading="loading.recentPosts"
    />
  </main>
</template>
