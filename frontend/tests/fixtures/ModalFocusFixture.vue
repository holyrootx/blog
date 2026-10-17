<script setup>
import { ref } from 'vue';
import BaseModal from '../../src/shared/components/BaseModal.vue';
import SearchBox from '../../src/features/search/components/SearchBox.vue';
import CommentReportDialog from '../../src/features/post/components/CommentReportDialog.vue';
import AdminTextInput from '../../src/features/admin/components/AdminTextInput.vue';
import '../../src/assets/scss/main.scss';
import '../../src/assets/scss/admin.scss';

const outer = ref(false);
const inner = ref(false);
const mounted = ref(true);
const report = ref(false);
const name = ref('작성 중인 이름');
const duplicate = ref('다른 입력');
const hideLast = ref(false);
const lastDisabled = ref(false);
</script>

<template>
  <main>
    <div style="height: 450px"></div>
    <button id="outside-before">앞의 버튼</button>
    <SearchBox v-if="mounted"><template #trigger="{ open }">
      <button id="search-trigger" @click="open">검색 열기</button>
    </template></SearchBox>
    <button id="outer-trigger" @click="outer = true">관리자 창 열기</button>
    <button id="report-trigger" @click="report = true">신고 창 열기</button>
    <button id="outside-after">뒤의 버튼</button>
    <div style="height: 1500px"></div>

    <!-- DOM 순서보다 나중에 연 창이 위에 있어야 한다. -->
    <BaseModal v-if="mounted" :open="inner" title="확인" size="small" @close="inner = false">
      <p>입력한 내용을 유지한 채 돌아갑니다.</p>
      <button id="inner-return" @click="inner = false">돌아가기</button>
      <button id="unmount" @click="mounted = false">화면 떠나기</button>
    </BaseModal>
    <BaseModal v-if="mounted" :open="outer" title="관리자 폼" variant="admin" :close-on-backdrop="false" @close="outer = false">
      <AdminTextInput v-model="name" label="이름" />
      <AdminTextInput v-model="duplicate" label="이름" />
      <input aria-label="내부에서 Escape 처리" @keydown.esc.prevent />
      <button id="nested-trigger" @click="inner = true">확인 창 열기</button>
      <button hidden>숨겨진 버튼</button>
      <button disabled>비활성 버튼</button>
      <button @click="hideLast = !hideLast">마지막 버튼 표시 전환</button>
      <button @click="lastDisabled = !lastDisabled">마지막 버튼 사용 전환</button>
      <div style="height: 600px"></div>
      <template #footer>
        <button v-if="!hideLast" id="last" :disabled="lastDisabled">마지막 버튼</button>
      </template>
    </BaseModal>
    <CommentReportDialog :open="report" target="테스트 독자" @close="report = false" />
  </main>
</template>
