// 첫 화면을 그리기 전에 고른 테마를 붙인다. 사정은 src/shared/theme/themeStore.js 에 적었다.
// 따로 뺀 파일이다 — 운영 CSP(script-src 'self')가 index.html 안에 적은 스크립트를 막는다.
try {
  const theme =window.localStorage.getItem('jsjlog-theme');
  if (theme === 'light' || theme === 'dark') document.documentElement.dataset.theme = theme;
} catch {
  // 저장소를 못 쓰면 기기 설정을 따른다
}
