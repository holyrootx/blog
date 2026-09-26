<script setup>
defineProps({
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
});
</script>

<template>
  <section class="home-hero" :aria-busy="heroLoading || profileLoading">
    <div class="home-hero__copy">
      <div v-if="heroLoading" class="home-hero__copy-skeleton" aria-hidden="true">
        <span class="ui-skeleton home-skeleton--eyebrow"></span>
        <span class="ui-skeleton home-skeleton--headline"></span>
        <span class="ui-skeleton home-skeleton--description"></span>
      </div>
      <template v-else>
        <p class="home-hero__eyebrow">{{ hero.subTitle }}</p>
        <h1 class="home-hero__headline">{{ hero.title }}</h1>
        <p class="home-hero__description">{{ hero.intro }}</p>
      </template>

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
