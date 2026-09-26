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
  sectionLoading: {
    type: Boolean,
    default: false,
  },
  topicsLoading: {
    type: Boolean,
    default: false,
  },
});
</script>

<template>
  <section
    v-if="sectionLoading || topicsLoading || topics.length > 0"
    class="home-section home-topics"
    aria-labelledby="home-topics-title"
    :aria-busy="sectionLoading || topicsLoading"
  >
    <div v-if="sectionLoading" class="home-topics__header" aria-hidden="true">
      <span class="ui-skeleton home-skeleton--section-title"></span>
      <span class="ui-skeleton home-skeleton--section-intro"></span>
    </div>
    <div v-else class="home-topics__header">
      <h2 id="home-topics-title" class="home-section__title">{{ section.title }}</h2>
      <p class="home-topics__intro">{{ section.intro }}</p>
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
