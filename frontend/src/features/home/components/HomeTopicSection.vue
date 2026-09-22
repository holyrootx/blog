<script setup>
defineProps({
  topics: {
    type: Array,
    required: true,
  },
});
</script>

<template>
  <section v-if="topics.length > 0" class="home-section home-topics" aria-labelledby="home-topics-title">
    <div class="home-topics__header">
      <h2 id="home-topics-title" class="home-section__title">여기서 다루는 이야기</h2>
      <p class="home-topics__intro">앞으로 이런 글을 쓸거에요.</p>
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
      <span class="home-topics__badge">쓰는 중</span>
      <span>아직 글이 많지 않아요. 하나씩 채워가는 중이에요.</span>
    </div>
  </section>
</template>
