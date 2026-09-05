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
});
</script>

<template>
  <section class="home-hero">
    <div class="home-hero__copy">
      <p class="home-hero__eyebrow">{{ hero.subTitle }}</p>
      <h1 class="home-hero__headline">{{ hero.title }}</h1>
      <p class="home-hero__description">{{ hero.intro }}</p>

      <div class="home-hero__profile">
        <img
          v-if="profile.avatarImageUrl"
          class="home-hero__avatar"
          :src="profile.avatarImageUrl"
          :alt="`${profile.name} 프로필`"
        />
        <div>
          <strong class="home-hero__profile-name">{{ profile.name }}</strong>
          <span class="home-hero__profile-label">{{ profile.job }}</span>
          <p class="home-hero__profile-bio">{{ profile.intro }}</p>
        </div>
      </div>
    </div>

    <img
      v-if="hero.heroImageUrl"
      class="home-hero__image"
      :src="hero.heroImageUrl"
      alt="노트북과 책상 위 장비가 놓인 작업 공간"
    />
  </section>
</template>
