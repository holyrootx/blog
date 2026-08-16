<script setup>
import { computed, onMounted, reactive } from 'vue';

import BlogHeader from '../components/BlogHeader.vue';
import HomeHero from '../components/HomeHero.vue';
import StatGrid from '../components/StatGrid.vue';
import PostSection from '../components/PostSection.vue';
import { getBlogProfile, getHomePageHero } from '../api/homeApi';
import { homeMock } from '../data/homeMock';

const home = reactive({
  header: { ...homeMock.header },
  hero: { ...homeMock.hero },
  profile: { ...homeMock.profile },
  stats: homeMock.stats,
  featuredPosts: homeMock.featuredPosts,
  recentPosts: homeMock.recentPosts,
});

const headerTitle = computed(() => home.profile.name || home.header.title);

onMounted(async () => {
  const [profileResult, heroResult] = await Promise.allSettled([
    getBlogProfile(),
    getHomePageHero(),
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
      <StatGrid :stats="home.stats" />
      <PostSection title="인기글" :posts="home.featuredPosts" />
      <PostSection title="최근 글" :posts="home.recentPosts" />
    </main>
  </div>
</template>
