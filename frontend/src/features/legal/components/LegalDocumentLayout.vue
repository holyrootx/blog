<script setup>
import { RouterLink } from 'vue-router';

defineProps({
  title: {
    type: String,
    required: true,
  },
  effectiveDate: {
    type: String,
    required: true,
  },
});
</script>

<template>
  <main class="legal-document">
    <article class="legal-document__body">
      <RouterLink class="legal-document__back" to="/">JSJ.log</RouterLink>
      <header class="legal-document__header">
        <h1>{{ title }}</h1>
        <p>시행일: {{ effectiveDate }}</p>
      </header>
      <slot />
    </article>
  </main>
</template>
