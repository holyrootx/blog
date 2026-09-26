<script setup>
import { computed, onMounted, reactive } from 'vue';

import BlogHeader from '../components/BlogHeader.vue';
import HomeHero from '../components/HomeHero.vue';
import PostSection from '../components/PostSection.vue';
import HomeTopicSection from '../components/HomeTopicSection.vue';
import {
  getBlogProfile,
  getHomePageHero,
  getHomePosts,
  getHomeTopics,
  getHomeTopicSection,
} from '../api/homeApi';

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

const EMPTY_TOPIC_SECTION = {
  title: '',
  intro: '',
  noteBadge: '',
  note: '',
};

const home = reactive({
  header: { title: SITE_TITLE },
  hero: { ...EMPTY_HERO },
  profile: { ...EMPTY_PROFILE },
  topicSection: { ...EMPTY_TOPIC_SECTION },
  topics: [],
  featuredPosts: [],
  recentPosts: [],
});

const loading = reactive({
  profile: true,
  hero: true,
  topicSection: true,
  topics: true,
  featuredPosts: true,
  recentPosts: true,
});

const headerTitle = computed(() => home.profile.name || home.header.title);

onMounted(() => {
  loadHomeData('profile', getBlogProfile, (profile) => {
    home.profile = mergeDefined(home.profile, profile);
  });
  loadHomeData('hero', getHomePageHero, (hero) => {
    home.hero = mergeDefined(home.hero, hero);
  });
  loadHomeData('topicSection', getHomeTopicSection, (section) => {
    home.topicSection = mergeDefined(home.topicSection, section);
  });
  loadHomeData('topics', getHomeTopics, (topics) => {
    home.topics = Array.isArray(topics) ? topics : [];
  });
  loadHomeData('featuredPosts', () => getHomePosts('popular'), (posts) => {
    home.featuredPosts = posts;
  });
  loadHomeData('recentPosts', () => getHomePosts('latest'), (posts) => {
    home.recentPosts = posts;
  });
});

function loadHomeData(key, request, apply) {
  request()
    .then(apply)
    .catch((error) => console.error(error))
    .finally(() => {
      loading[key] = false;
    });
}

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
      <HomeHero
        :hero="home.hero"
        :profile="home.profile"
        :hero-loading="loading.hero"
        :profile-loading="loading.profile"
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
    <footer class="public-footer">
      <RouterLink to="/privacy">개인정보처리방침</RouterLink>
      <RouterLink to="/terms">서비스 이용약관</RouterLink>
    </footer>
  </div>
</template>
