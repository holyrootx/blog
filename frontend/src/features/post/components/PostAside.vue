<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = defineProps({
  toc: {
    type: Array,
    required: true,
  },
  ads: {
    type: Array,
    required: true,
  },
  showAds: {
    type: Boolean,
    default: false,
  },
});

// 현재 보고 있는 절. 해시(#section-2)로 이동하면 따라 바뀐다
const activeId = ref(getActiveId());
const closed = ref([]);

function syncActive() {
  activeId.value = getActiveId();
}

function getActiveId() {
  return window.location.hash.slice(1) || props.toc[0]?.id || '';
}

function close(index) {
  closed.value = [...closed.value, index];
}

onMounted(() => window.addEventListener('hashchange', syncActive));
onBeforeUnmount(() => window.removeEventListener('hashchange', syncActive));

watch(() => props.toc, syncActive);
</script>

<template>
  <aside class="post-aside" :aria-label="showAds ? '목차 및 광고' : '목차'">
    <div v-if="showAds" class="post-aside__head">
      <span class="post-aside__sponsored">SPONSORED</span>
      <span class="post-aside__rule"></span>
      <span class="post-aside__note">본문과 무관한 영역</span>
    </div>

    <div v-if="showAds && ads[0] && !closed.includes(0)" class="ad-card">
      <div class="ad-card__head">
        <span class="ad-card__label">광고 · AD</span>
        <button class="ad-card__close" type="button" aria-label="광고 닫기" @click="close(0)">×</button>
      </div>
      <div class="ad-card__slot" :style="{ height: `${ads[0].height}px` }">{{ ads[0].label }}</div>
    </div>

    <!-- 목차와 두 번째 광고가 함께 스크롤을 따라온다 -->
    <div class="post-aside__sticky">
      <nav v-if="toc.length > 0" class="post-toc" aria-label="목차">
        <div class="post-toc__head">
          <h2 class="post-toc__title">목차</h2>
          <span class="post-toc__hint">읽는 위치 따라 이동</span>
        </div>
        <ul class="post-toc__list">
          <li v-for="item in toc" :key="item.id">
            <a
              class="post-toc__link"
              :class="{ 'post-toc__link--active': activeId === item.id }"
              :href="`#${item.id}`"
              :aria-current="activeId === item.id ? 'location' : undefined"
            >
              {{ item.text }}
            </a>
          </li>
        </ul>
      </nav>

      <div v-if="showAds && ads[1] && !closed.includes(1)" class="ad-card">
        <div class="ad-card__head">
          <span class="ad-card__label">광고 · AD (스크롤 고정)</span>
          <button class="ad-card__close" type="button" aria-label="광고 닫기" @click="close(1)">
            ×
          </button>
        </div>
        <div class="ad-card__slot" :style="{ height: `${ads[1].height}px` }">{{ ads[1].label }}</div>
      </div>
    </div>
  </aside>
</template>
