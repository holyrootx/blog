<script setup>
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
  <main class="public-shell__main legal-document">
    <article class="legal-document__body">
      <header class="legal-document__header">
        <h1>{{ title }}</h1>
        <p>시행일: {{ effectiveDate }}</p>
      </header>
      <slot />
    </article>
  </main>
</template>
