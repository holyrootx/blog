<script setup>
import { computed } from 'vue';

// 표. columns 로 열을 정의하고, 특별한 셀은 #cell-{key} 슬롯으로 갈아끼운다.
const props = defineProps({
  // [{ key, label, width?, align? }]
  columns: {
    type: Array,
    required: true,
  },
  rows: {
    type: Array,
    default: () => [],
  },
  rowKey: {
    type: String,
    default: 'id',
  },
  loading: {
    type: Boolean,
    default: false,
  },
  emptyText: {
    type: String,
    default: '조회된 데이터가 없습니다.',
  },
  errorText: {
    type: String,
    default: '',
  },
  // 행을 누르면 상세 팝업이 열리는 표인지.
  // true 일 때만 커서가 pointer 로 바뀌고 row-click 이 발생한다.
  rowClickable: {
    type: Boolean,
    default: false,
  },
  // 일괄 삭제처럼 여러 행을 골라야 하는 표인지.
  // true 면 첫 열에 체크박스가 붙는다.
  selectable: {
    type: Boolean,
    default: false,
  },
  // 선택된 행의 rowKey 값 배열. v-model:selected-keys 로 쓴다
  selectedKeys: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(['row-click', 'update:selectedKeys', 'retry']);

const state = computed(() => {
  if (props.loading) return 'loading';
  if (props.errorText) return 'error';
  if (props.rows.length === 0) return 'empty';
  return 'rows';
});

// 메시지 줄이 차지할 칸 수 (체크박스 열 포함)
const messageColspan = computed(() => props.columns.length + (props.selectable ? 1 : 0));

const pageKeys = computed(() => props.rows.map((row) => row[props.rowKey]));

// 헤더 체크박스는 "현재 페이지 전체"를 대상으로 한다
const allChecked = computed(
  () => pageKeys.value.length > 0
    && pageKeys.value.every((key) => props.selectedKeys.includes(key)),
);

const someChecked = computed(
  () => !allChecked.value
    && pageKeys.value.some((key) => props.selectedKeys.includes(key)),
);

function isChecked(row) {
  return props.selectedKeys.includes(row[props.rowKey]);
}

function toggleRow(row) {
  const key = row[props.rowKey];
  const next = isChecked(row)
    ? props.selectedKeys.filter((selected) => selected !== key)
    : [...props.selectedKeys, key];

  emit('update:selectedKeys', next);
}

function toggleAll() {
  if (allChecked.value) {
    // 현재 페이지만 해제하고 다른 페이지 선택은 남긴다
    emit('update:selectedKeys', props.selectedKeys.filter((key) => !pageKeys.value.includes(key)));
    return;
  }

  const merged = new Set([...props.selectedKeys, ...pageKeys.value]);
  emit('update:selectedKeys', [...merged]);
}

function onRowClick(row) {
  if (!props.rowClickable) {
    return;
  }

  emit('row-click', row);
}
</script>

<template>
  <div class="admin-grid">
    <table class="admin-grid__table" :class="{ 'admin-grid__table--clickable': rowClickable }">
      <colgroup>
        <col v-if="selectable" style="width: 46px" />
        <col v-for="column in columns" :key="column.key" :style="{ width: column.width }" />
      </colgroup>

      <thead>
        <tr>
          <th v-if="selectable" class="admin-grid__check-cell">
            <input
              type="checkbox"
              :checked="allChecked"
              :indeterminate.prop="someChecked"
              :disabled="rows.length === 0"
              aria-label="이 페이지 전체 선택"
              @change="toggleAll"
            />
          </th>
          <th
            v-for="column in columns"
            :key="column.key"
            :style="{ textAlign: column.align ?? 'left' }"
          >
            {{ column.label }}
          </th>
        </tr>
      </thead>

      <tbody>
        <!-- 로딩: 문구 대신 뼈대.
             td 를 직접 flex 로 만들면 셀 너비가 무너지므로 안쪽 div 에서 정렬한다 -->
        <tr v-if="state === 'loading'">
          <td :colspan="messageColspan">
            <div class="admin-grid__skeleton">
              <span></span><span></span><span></span>
            </div>
          </td>
        </tr>

        <!-- 실패: 아이콘 + 오류색 + 재시도 -->
        <tr v-else-if="state === 'error'">
          <td :colspan="messageColspan">
            <div class="admin-grid__error">
              <span class="admin-grid__error-icon" aria-hidden="true">!</span>
              <span>{{ errorText }}</span>
              <button class="admin-grid__retry" type="button" @click="emit('retry')">
                다시 시도
              </button>
            </div>
          </td>
        </tr>

        <tr v-else-if="state === 'empty'">
          <td class="admin-grid__message" :colspan="messageColspan">{{ emptyText }}</td>
        </tr>

        <template v-else>
          <tr
            v-for="row in rows"
            :key="row[rowKey]"
            :class="{ 'admin-grid__row--checked': selectable && isChecked(row) }"
            @click="onRowClick(row)"
          >
            <!-- 체크박스를 눌렀을 때 행 클릭(팝업)이 같이 일어나지 않게 막는다 -->
            <td v-if="selectable" class="admin-grid__check-cell" @click.stop>
              <input
                type="checkbox"
                :checked="isChecked(row)"
                :aria-label="`${row[rowKey]}번 선택`"
                @change="toggleRow(row)"
              />
            </td>
            <td
              v-for="column in columns"
              :key="column.key"
              :style="{ textAlign: column.align ?? 'left' }"
            >
              <slot :name="`cell-${column.key}`" :row="row" :value="row[column.key]">
                {{ row[column.key] ?? '-' }}
              </slot>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>
</template>
