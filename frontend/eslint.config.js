import js from '@eslint/js';
import pluginVue from 'eslint-plugin-vue';
import globals from 'globals';

/**
 * 코드가 틀린 곳을 잡는 규칙만 켠다. 줄바꿈·속성 순서 같은 모양 규칙은 켜지 않는다 —
 * 켜면 기존 파일 수백 곳이 한꺼번에 걸리고, 고치려면 동작과 무관한 재포맷이 섞인다(컨벤션 §8).
 */
export default [
  { ignores: ['dist/**', 'src/sandbox/**'] },
  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  {
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser },
    },
  },
  {
    files: ['tests/**/*.mjs', 'tests/**/*.js', 'vite.config.js', 'eslint.config.js'],
    languageOptions: { globals: { ...globals.node } },
  },
];
