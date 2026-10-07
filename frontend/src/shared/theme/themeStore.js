import { readonly, ref } from 'vue';

/**
 * 화면 밝기. 처음에는 기기 설정을 따르고, 헤더 단추로 고르면 이 브라우저에 기억한다.
 *
 * 고른 값은 localStorage 와 html 의 data-theme 에 둔다. 색은 CSS 가 data-theme 과 기기 설정을
 * 보고 고른다(base/_tokens.scss). 첫 화면이 그려지기 전에 public/theme-init.js 가 같은 키를 읽어
 * data-theme 을 먼저 붙인다 — 앱 코드에서 처음 붙이면 코드를 받는 동안 기기 설정 색으로
 * 한 번 그려졌다가 바뀐다.
 *
 * 기기 설정과 같은 쪽을 고르면 기억을 지운다. 그래야 나중에 기기 설정을 바꿨을 때 다시 따라간다.
 */
export const THEME_STORAGE_KEY = 'jsjlog-theme';

const THEMES = ['light', 'dark'];

/** 기억한 값이 있으면 그것, 없으면 기기 설정 */
export function resolveTheme(stored, systemDark) {
  if (THEMES.includes(stored)) return stored;
  return systemDark ? 'dark' : 'light';
}

/** 지금 반대쪽으로 바꿀 때 보일 테마와 기억할 값. 기기 설정과 같으면 기억하지 않는다(null) */
export function nextThemeChoice(current, systemDark) {
  const theme = current === 'dark' ? 'light' : 'dark';
  const system = systemDark ? 'dark' : 'light';
  return { theme, stored: theme === system ? null : theme };
}

const media = typeof window !== 'undefined' && window.matchMedia
  ? window.matchMedia('(prefers-color-scheme: dark)')
  : null;

const storageAvailable = ref(true);
const stored = readStored();
const theme = ref(resolveTheme(stored, media?.matches));

// theme-init.js 를 못 받았어도 기억한 값은 지킨다
applyStored(stored);

// 저장소 접근이 도중에 막혔어도 화면과 단추가 같은 기기 설정을 따르게 한다.
media?.addEventListener('change', () => {
  const remembered = storageAvailable.value ? readStored() : null;
  applyStored(remembered);
  theme.value = resolveTheme(remembered, media.matches);
});

export const currentTheme = readonly(theme);
export const canChooseTheme = readonly(storageAvailable);

export function toggleTheme() {
  const next = nextThemeChoice(theme.value, media?.matches);
  if (!storageAvailable.value || !writeStored(next.stored)) {
    applyStored(null);
    theme.value = resolveTheme(null, media?.matches);
    return;
  }
  applyStored(next.stored);
  theme.value = next.theme;
}

// 사생활 보호 창이나 저장소를 막은 브라우저에서는 읽기·쓰기가 예외를 던진다.
// 그때는 기억 없이 기기 설정만 따른다
function readStored() {
  try {
    return window.localStorage.getItem(THEME_STORAGE_KEY);
  } catch {
    storageAvailable.value = false;
    return null;
  }
}

function writeStored(value) {
  try {
    if (value) window.localStorage.setItem(THEME_STORAGE_KEY, value);
    else window.localStorage.removeItem(THEME_STORAGE_KEY);
    return true;
  } catch {
    // 저장에 실패했는데 화면만 바꾸면 다음 기기 설정 변경 때 단추와 색이 어긋난다.
    storageAvailable.value = false;
    return false;
  }
}

function applyStored(value) {
  if (typeof document === 'undefined') return;
  if (THEMES.includes(value)) document.documentElement.dataset.theme = value;
  else delete document.documentElement.dataset.theme;
}
