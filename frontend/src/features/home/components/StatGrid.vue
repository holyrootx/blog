<script setup>
defineProps({
  stats: {
    type: Array,
    required: true,
  },
});
</script>

<template>
  <section class="home-section" aria-labelledby="stats-title">
    <h2 id="stats-title" class="home-section__title">기록의 숫자</h2>
    <div class="stat-grid">
      <article v-for="stat in stats" :key="stat.label" class="stat-card">
        <span class="stat-card__label">{{ stat.label }}</span>
        <strong class="stat-card__value">{{ stat.value }}</strong>
      </article>
    </div>
  </section>
</template>
