/**
 * 코드 색칠.
 *
 * 하이라이터 라이브러리를 넣지 않고 직접 만든다. 라이브러리는 수십 개 언어 정의를
 * 통째로 들고 와서 번들이 커지는데, 이 블로그에 실제로 올라갈 언어는 몇 개다.
 *
 * 반환값은 { text, kind } 배열이다. HTML 문자열을 만들지 않는 이유는
 * 렌더 경로에 주입 구멍을 두지 않기 위해서다 — 본문 매퍼와 같은 원칙.
 */

/** 코드 블록에서 고를 수 있는 언어 */
export const CODE_LANGUAGES = [
  { value: '', label: '언어 없음' },
  { value: 'java', label: 'Java' },
  { value: 'javascript', label: 'JavaScript' },
  { value: 'typescript', label: 'TypeScript' },
  { value: 'vue', label: 'Vue' },
  { value: 'python', label: 'Python' },
  { value: 'sql', label: 'SQL' },
  { value: 'bash', label: 'Bash' },
  { value: 'json', label: 'JSON' },
  { value: 'yaml', label: 'YAML' },
  { value: 'html', label: 'HTML' },
  { value: 'css', label: 'CSS' },
];

const JAVA = split(`abstract assert boolean break byte case catch char class const continue default do
  double else enum extends final finally float for goto if implements import instanceof int interface
  long native new package private protected public record return sealed short static strictfp super
  switch synchronized this throw throws transient try var void volatile while yield true false null`);

const JS = split(`async await break case catch class const continue debugger default delete do else
  export extends finally for from function get if import in instanceof let new of return set static
  super switch this throw try typeof var void while with yield true false null undefined`);

const TS = [...JS, ...split(`abstract any as boolean declare enum implements interface is keyof
  namespace never number override private protected public readonly string type unknown`)];

const PYTHON = split(`and as assert async await break class continue def del elif else except finally
  for from global if import in is lambda nonlocal not or pass raise return try while with yield
  True False None self`);

const SQL = split(`select insert update delete from where join inner left right outer full on group by
  having order limit offset union all distinct as into values set create alter drop table index view
  primary key foreign references not null default and or in like between exists case when then else end`);

const BASH = split(`if then else elif fi for while do done case esac function return export local
  readonly source echo cd exit set unset`);

const JSON_WORDS = split('true false null');

const YAML_WORDS = split('true false null yes no on off');

const CSS_WORDS = split(`important media supports keyframes import from to and not only screen print`);

/** 언어별 설정. 없는 언어는 C 계열로 가정한다 */
const LANGUAGES = {
  java: { keywords: JAVA, lineComment: ['//'], blockComment: true },
  javascript: { keywords: JS, lineComment: ['//'], blockComment: true },
  typescript: { keywords: TS, lineComment: ['//'], blockComment: true },
  vue: { keywords: TS, lineComment: ['//'], blockComment: true, markup: true },
  python: { keywords: PYTHON, lineComment: ['#'], blockComment: false },
  sql: { keywords: SQL, lineComment: ['--'], blockComment: true, ignoreCase: true },
  bash: { keywords: BASH, lineComment: ['#'], blockComment: false },
  json: { keywords: JSON_WORDS, lineComment: [], blockComment: false },
  yaml: { keywords: YAML_WORDS, lineComment: ['#'], blockComment: false },
  html: { keywords: [], lineComment: [], blockComment: false, markup: true },
  css: { keywords: CSS_WORDS, lineComment: [], blockComment: true },
};

const FALLBACK = { keywords: [...JAVA, ...JS], lineComment: ['//', '#'], blockComment: true };

function split(text) {
  return text.trim().split(/\s+/);
}

function configFor(language) {
  const name = String(language ?? '').toLowerCase();

  if (!name) {
    return FALLBACK;
  }

  const alias = { js: 'javascript', ts: 'typescript', py: 'python', sh: 'bash', shell: 'bash', yml: 'yaml', xml: 'html' };

  return LANGUAGES[alias[name] ?? name] ?? FALLBACK;
}

export function toCodeTokens(code, language) {
  const source = String(code ?? '');
  const config = configFor(language);
  const keywords = new Set(config.ignoreCase
    ? config.keywords.map((word) => word.toLowerCase())
    : config.keywords);

  const tokens = [];
  let index = 0;
  let plain = '';

  function pushPlain() {
    if (plain) {
      tokens.push({ text: plain, kind: 'plain' });
      plain = '';
    }
  }

  function push(text, kind) {
    pushPlain();
    tokens.push({ text, kind });
  }

  while (index < source.length) {
    const rest = source.slice(index);

    if (config.blockComment && rest.startsWith('/*')) {
      const end = source.indexOf('*/', index + 2);
      const stop = end === -1 ? source.length : end + 2;
      push(source.slice(index, stop), 'comment');
      index = stop;
      continue;
    }

    // 마크업은 주석 모양이 다르다
    if (config.markup && rest.startsWith('<!--')) {
      const end = source.indexOf('-->', index + 4);
      const stop = end === -1 ? source.length : end + 3;
      push(source.slice(index, stop), 'comment');
      index = stop;
      continue;
    }

    const marker = config.lineComment.find((item) => rest.startsWith(item));
    if (marker) {
      const end = source.indexOf('\n', index);
      const stop = end === -1 ? source.length : end;
      push(source.slice(index, stop), 'comment');
      index = stop;
      continue;
    }

    // 태그 이름. <div ...> 의 div 를 칠한다
    if (config.markup) {
      const tag = rest.match(/^<\/?([A-Za-z][\w-]*)/);
      if (tag) {
        push(tag[0], 'tag');
        index += tag[0].length;
        continue;
      }
    }

    const quote = rest[0];
    if (quote === '"' || quote === "'" || quote === '`') {
      let cursor = index + 1;

      while (cursor < source.length) {
        if (source[cursor] === '\\') {
          cursor += 2;
          continue;
        }

        // 줄을 넘어가면 닫히지 않은 것으로 보고 거기서 끊는다
        if (source[cursor] === quote || (quote !== '`' && source[cursor] === '\n')) {
          break;
        }

        cursor += 1;
      }

      const stop = Math.min(cursor + 1, source.length);
      push(source.slice(index, stop), 'string');
      index = stop;
      continue;
    }

    const number = rest.match(/^\d[\d_.]*/);
    if (number && !/[\w$]/.test(source[index - 1] ?? '')) {
      push(number[0], 'number');
      index += number[0].length;
      continue;
    }

    const word = rest.match(/^[A-Za-z_$@][\w$-]*/);
    if (word) {
      const value = word[0];
      const lookup = config.ignoreCase ? value.toLowerCase() : value;

      if (keywords.has(lookup)) {
        push(value, 'keyword');
      } else if (/^[A-Z]/.test(value) && !config.markup) {
        // 클래스·타입 이름. 자바에서 특히 눈에 띄어야 한다
        push(value, 'type');
      } else {
        plain += value;
      }

      index += value.length;
      continue;
    }

    plain += source[index];
    index += 1;
  }

  pushPlain();

  return tokens;
}
