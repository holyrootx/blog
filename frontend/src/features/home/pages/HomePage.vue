<script setup>
import { computed, onMounted, reactive } from 'vue';

import BlogHeader from '../components/BlogHeader.vue';
import HomeHero from '../components/HomeHero.vue';
import PostSection from '../components/PostSection.vue';
import HomeTopicSection from '../components/HomeTopicSection.vue';
import { getBlogProfile, getHomePageHero, getHomePosts, getHomeTopics } from '../api/homeApi';

// API 응답이 오기 전까지의 초기 상태. mergeDefined가 빈 값을 덮어쓰지 않으므로
// 여기 남은 값은 API가 해당 필드를 내려주지 않았을 때 그대로 노출된다.
const SITE_TITLE = 'JSJ.log';

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

const home = reactive({
  header: { title: SITE_TITLE },
  hero: { ...EMPTY_HERO },
  profile: { ...EMPTY_PROFILE },
  topics: [],
  featuredPosts: [],
  recentPosts: [],
});

const headerTitle = computed(() => home.profile.name || home.header.title);

onMounted(async () => {
  const [profileResult, heroResult, topicsResult, popularPostsResult, latestPostsResult] = await Promise.allSettled([
    getBlogProfile(),
    getHomePageHero(),
    getHomeTopics(),
    getHomePosts('popular'),
    getHomePosts('latest'),
  ]);

  if (profileResult.status === 'fulfilled') {
    home.profile = mergeDefined(home.profile, profileResult.value);
  } else {
    console.error(profileResult.reason);
  }

  if (heroResult.status === 'fulfilled') {
    home.hero = mergeDefined(home.hero, heroResult.value);
  } else {
    console.error(heroResult.reason);
  }

  if (topicsResult.status === 'fulfilled') {
    home.topics = Array.isArray(topicsResult.value) ? topicsResult.value : [];
  } else {
    console.error(topicsResult.reason);
  }

  if (popularPostsResult.status === 'fulfilled') {
    home.featuredPosts = popularPostsResult.value;
  } else {
    console.error(popularPostsResult.reason);
  }

  if (latestPostsResult.status === 'fulfilled') {
    home.recentPosts = latestPostsResult.value;
  } else {
    console.error(latestPostsResult.reason);
  }
});

function mergeDefined(base, next) {
  return Object.entries(next ?? {}).reduce(
    (result, [key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        result[key] = value;
      }

      return result;
    },
    { ...base },
  );
}
</script>

<template>
  <div class="public-shell">
    <BlogHeader :title="headerTitle" />
    <main class="public-shell__main">
      <HomeHero :hero="home.hero" :profile="home.profile" />
      <HomeTopicSection :topics="home.topics" />
      <PostSection title="인기글" :posts="home.featuredPosts" />
      <PostSection title="최근 글" :posts="home.recentPosts" />
    </main>
  </div>
</template>
