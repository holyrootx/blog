/** datetime-local 입력이 쓰는 형식(YYYY-MM-DDTHH:mm)으로. UTC 로 바꾸면 시간이 밀린다 */
export function toLocalInputValue(date) {
  const pad = (number) => String(number).padStart(2, '0');

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 편집 화면 작업 바와 복구 창이 쓰는 표기 (2026.09.24 14:05) */
export function formatEditorDateTime(value) {
  if (!value) {
    return '';
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return '';
  }

  const pad = (number) => String(number).padStart(2, '0');

  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
