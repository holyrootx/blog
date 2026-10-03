<script setup>
import { computed, ref } from 'vue';
import { DEFAULT_IMAGE_ALIGN, MAX_IMAGE_WIDTH } from '../../../shared/post/postImageMarkdown';

const props = defineProps({
  block: { type: Object, required: true },
  uploading: Boolean,
  resizing: Boolean,
  error: { type: String, default: '' },
});
const emit = defineEmits(['select', 'resize-start', 'resize-keydown', 'align', 'caption', 'pick']);
const captionInput = ref(null);
const imageWidth = computed(() => props.block.width || MAX_IMAGE_WIDTH);
const imageAlign = computed(() => props.block.align || DEFAULT_IMAGE_ALIGN);
const ALIGN_OPTIONS = [
  { value: 'left', label: '왼쪽 정렬' },
  { value: 'center', label: '가운데 정렬' },
  { value: 'right', label: '오른쪽 정렬' },
];
</script>

<template>
  <div class="block-editor__image">
    <template v-if="block.previewUrl || block.url">
      <!-- 폭을 가진 틀. 공개 화면의 figure 와 같은 자리를 차지해서
           여기서 보이는 크기가 곧 발행 결과다 -->
      <div
        class="block-editor__image-frame"
        :class="[
          `block-editor__image-frame--${imageAlign}`,
          { 'block-editor__image-frame--resizing': resizing },
        ]"
        :style="{ width: `${imageWidth}%` }"
      >
        <!-- 손잡이를 이미지에만 맞춰 놓기 위한 칸. 캡션까지 묶으면
             손잡이가 캡션 높이만큼 아래로 내려간다 -->
        <div class="block-editor__image-canvas">
          <img
            class="block-editor__image-preview"
            :class="{ 'block-editor__image-preview--uploading': uploading }"
            :src="block.previewUrl || block.url"
            :alt="block.alt"
            draggable="false"
            @click="emit('select', $event)"
          />

          <!-- 좌우 손잡이. draggable=false 가 없으면 블록 순서 바꾸기가 먼저 물린다 -->
          <button
            v-for="side in ['left', 'right']"
            :key="side"
            class="block-editor__image-grip"
            :class="`block-editor__image-grip--${side}`"
            type="button"
            draggable="false"
            :aria-label="`이미지 폭 조절, 현재 ${imageWidth}%`"
            @pointerdown="emit('resize-start', side, $event)"
            @keydown="emit('resize-keydown', $event)"
          ></button>

          <!-- 사진 오른쪽 위 단추들. 정렬 셋과 캡션 하나 -->
          <div class="block-editor__image-tools">
            <div class="block-editor__image-toolgroup" role="group" aria-label="이미지 정렬">
              <button
                v-for="option in ALIGN_OPTIONS"
                :key="option.value"
                class="block-editor__image-align"
                :class="`block-editor__image-align--${option.value}`"
                type="button"
                draggable="false"
                :title="option.label"
                :aria-label="option.label"
                :aria-pressed="imageAlign === option.value"
                @click="emit('align', option.value)"
              ></button>
            </div>

            <span class="block-editor__image-tooldivider" aria-hidden="true"></span>

            <button
              class="block-editor__image-captionbutton"
              type="button"
              draggable="false"
              :title="block.alt ? '캡션 고치기' : '캡션 쓰기'"
              :aria-label="block.alt ? '캡션 고치기' : '캡션 쓰기'"
              @click="captionInput?.focus()"
            ></button>
          </div>

          <!-- 끄는 동안에만 숫자를 띄운다. 항상 떠 있으면 사진을 가린다.
               정렬 버튼이 오른쪽 위에 있어서 왼쪽으로 비켜 둔다 -->
          <span v-if="resizing" class="block-editor__image-size">
            {{ imageWidth }}%
          </span>
        </div>

        <!-- 캡션은 평소에 숨어 있다가 이미지에 마우스를 올리면 나타난다.
             항상 떠 있으면 사진마다 빈 입력칸이 한 줄씩 따라다닌다.
             여기 적은 값이 공개 화면의 캡션이자 대체 텍스트가 된다 -->
        <input
          ref="captionInput"
          class="block-editor__image-caption"
          :class="{ 'block-editor__image-caption--filled': block.alt }"
          type="text"
          :value="block.alt"
          placeholder="캡션 추가"
          aria-label="이미지 캡션"
          @input="emit('caption', $event.target.value)"
        />
      </div>
    </template>

    <p v-if="uploading" class="block-editor__image-status">올리는 중…</p>

    <button
      v-if="!block.previewUrl && !block.url && !uploading"
      class="block-editor__image-placeholder block-editor__image-placeholder--button"
      type="button"
      @click="emit('pick')"
    >
      <span class="block-editor__image-icon" aria-hidden="true"></span>
      이미지 추가
    </button>

    <p v-if="error" class="block-editor__image-error" role="alert">
      {{ error }}
      <button class="block-editor__image-retry" type="button" @click="emit('pick')">
        다시 고르기
      </button>
    </p>
  </div>
</template>
