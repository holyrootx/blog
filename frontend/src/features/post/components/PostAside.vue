<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { SCROLL_OFFSET } from '../../../app/router/scrollToHash';

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

/**
 * 지금 읽고 있는 절.
 *
 * <p>굴리는 대로 따라 바뀐다. 전에는 목차를 눌렀을 때만 바뀌었는데, 머리에는
 * "읽는 위치 따라 이동" 이라고 적혀 있었다 — 적힌 말과 하는 일이 달랐다.</p>
 */
const activeId = ref('');
const closed = ref([]);

/** 기준선을 제목 바로 위가 아니라 조금 아래에 둔다. 딱 붙여 두면 경계에서 깜빡인다 */
const ACTIVATE_MARGIN = 8;

let frame = null;

/**
 * 굴릴 때마다 계산하지 않는다.
 *
 * <p>스크롤 이벤트는 손가락 한 번에 수십 번 온다. 그때마다 제목들의 위치를 재면
 * 화면이 버벅인다. 다음 그림을 그리기 직전에 한 번만 계산한다.</p>
 */
function scheduleSync() {
  if (frame !== null) {
    return;
  }

  frame = requestAnimationFrame(() => {
    frame = null;
    syncActive();
  });
}

function syncActive() {
  const headings = props.toc
    .map((item) => document.getElementById(item.id))
    .filter((found) => found !== null);

  if (headings.length === 0) {
    activeId.value = '';
    return;
  }

  // 문서 끝에 닿으면 마지막 절로 본다. 마지막 절이 짧으면 기준선까지 못 올라와서
  // 끝까지 내려도 켜지지 않는다 — 다 읽었는데 목차는 앞 절을 가리키는 꼴이 된다
  const reachedBottom =
    window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2;

  if (reachedBottom) {
    activeId.value = headings[headings.length - 1].id;
    return;
  }

  // 기준선을 지나쳐 올라간 제목 중 마지막 것이 지금 읽는 절이다.
  // 헤더가 화면 위를 덮고 있어서 그 높이만큼 기준선을 내린다
  const line = SCROLL_OFFSET + ACTIVATE_MARGIN;
  let current = headings[0];

  for (const heading of headings) {
    if (heading.getBoundingClientRect().top > line) {
      break;
    }

    current = heading;
  }

  activeId.value = current.id;
}

function close(index) {
  closed.value = [...closed.value, index];
}

onMounted(() => {
  window.addEventListener('scroll', scheduleSync, { passive: true });
  window.addEventListener('resize', scheduleSync, { passive: true });
});

onBeforeUnmount(() => {
  window.removeEventListener('scroll', scheduleSync);
  window.removeEventListener('resize', scheduleSync);

  if (frame !== null) {
    cancelAnimationFrame(frame);
  }
});

// 본문이 늦게 도착하므로 목차가 생긴 뒤에 잰다. 그 전에는 제목 요소가 화면에 없다
watch(() => props.toc, () => nextTick(syncActive), { immediate: true });
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
