<script setup>
defineProps({
  section: {
    type: Object,
    required: true,
  },
  topics: {
    type: Array,
    required: true,
  },
});
</script>

<template>
  <section v-if="topics.length > 0" class="home-section home-topics" aria-labelledby="home-topics-title">
    <div class="home-topics__header">
      <h2 id="home-topics-title" class="home-section__title">{{ section.title }}</h2>
      <p class="home-topics__intro">{{ section.intro }}</p>
    </div>

    <div class="home-topic-grid">
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

    <div class="home-topics__note">
      <span class="home-topics__badge">{{ section.noteBadge }}</span>
      <span>{{ section.note }}</span>
    </div>
  </section>
</template>
