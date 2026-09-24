<script setup>
/**
 * 댓글 단추에 들어가는 그림.
 *
 * <p>이모지(👍 👎 🚨)를 쓰지 않는 이유는 기기마다 다르게 그려지기 때문이다. 맥에서 본
 * 모양이 윈도우·안드로이드에서 다르고, 색을 바꿀 수 없어서 "빨간 사이렌" 이 기기에 따라
 * 빨갛지 않을 수 있다. 이 블로그의 다른 아이콘(헤더 돋보기·햄버거)도 전부 직접 그린다.</p>
 *
 * <p>{@code currentColor} 로 칠하므로 색은 바깥의 CSS 가 정한다 — 누른 상태에서 색이
 * 들어오는 것도 여기가 아니라 단추 쪽에서 한다.</p>
 */
defineProps({
  name: {
    type: String,
    required: true,
    validator: (value) => ['like', 'dislike', 'report'].includes(value),
  },
});
</script>

<template>
  <!-- 뜻은 옆 글자와 aria-label 이 전한다. 그림 자체는 읽을 것이 없다 -->
  <svg
    class="comment-icon"
    viewBox="0 0 24 24"
    width="15"
    height="15"
    aria-hidden="true"
    focusable="false"
  >
    <!--
      따봉과 우우우는 같은 그림이다. 뒤집어 쓰면 두 벌을 따로 손보지 않아도 되고,
      두 단추의 크기와 무게가 저절로 같아진다
    -->
    <g v-if="name === 'like' || name === 'dislike'" :transform="name === 'dislike' ? 'rotate(180 12 12)' : undefined">
      <path
        d="M3 10.5h3.2v10H3a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1z"
        fill="currentColor"
      />
      <path
        d="M6.8 10.2 10.9 2.4A.9.9 0 0 1 12 2a2.6 2.6 0 0 1 2.6 2.6v4h4.6a2.2 2.2 0 0 1 2.1 2.7l-1.5 6.6a2.2 2.2 0 0 1-2.1 1.7H6.8z"
        fill="currentColor"
      />
    </g>

    <!--
      사이렌: 빛 + 돔 + 목 + 받침.
      받침을 돔에 붙이면 15px 에서 그냥 반원으로 보인다. 사이 틈과 좁은 목이 있어야
      "위에 얹힌 경광등" 으로 읽힌다
    -->
    <g v-else>
      <path
        d="M12 1.5v2.6M5 4.6l1.8 1.9M19 4.6l-1.8 1.9"
        stroke="currentColor"
        stroke-width="2.2"
        stroke-linecap="round"
        fill="none"
      />
      <path d="M6 16.2a6 6 0 0 1 12 0z" fill="currentColor" />
      <rect x="9.6" y="16.2" width="4.8" height="2" fill="currentColor" />
      <rect x="4" y="19.4" width="16" height="3.1" rx="1.55" fill="currentColor" />
    </g>
  </svg>
</template>
