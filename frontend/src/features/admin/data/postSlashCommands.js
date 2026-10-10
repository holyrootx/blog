/**
 * 슬래시 메뉴에 뜨는 항목들.
 *
 * 저장되는 값은 그대로 마크다운이다. 이 메뉴는 문법을 외우지 않아도 되게 돕는
 * 입력 보조일 뿐, 유일한 입력 수단이 아니다 — `## `를 직접 쳐도 결과는 같다.
 *
 * shortcut 은 메뉴에 그대로 보여준다. 치면서 익히라고 두는 것이지,
 * 이 글자로만 찾히는 것은 아니다 (keywords 로도 찾힌다).
 *
 * kind
 *  - line     : 현재 줄 맨 앞에 접두어를 붙인다 (제목·목록·인용)
 *  - block    : 커서 자리에 여러 줄 틀을 넣는다 (코드·콜아웃·구분선)
 *  - inline   : 커서 자리에 감싸는 문법을 넣고 안쪽 글자를 선택한다 (굵게·링크)
 */
export const POST_SLASH_COMMANDS = [
  {
    id: 'h1',
    label: '제목 1',
    hint: '# 큰 제목',
    shortcut: 'h1',
    keywords: ['h1', 'head1', 'heading1', '제목1', '큰제목', '헤딩', 'title'],
    kind: 'line',
    prefix: '# ',
  },
  {
    id: 'h2',
    label: '제목 2',
    hint: '## 중간 제목',
    shortcut: 'h2',
    keywords: ['h2', 'head2', 'heading2', '제목2', '중간제목', '헤딩'],
    kind: 'line',
    prefix: '## ',
  },
  {
    id: 'h3',
    label: '제목 3',
    hint: '### 작은 제목',
    shortcut: 'h3',
    keywords: ['h3', 'head3', 'heading3', '제목3', '작은제목', '헤딩'],
    kind: 'line',
    prefix: '### ',
  },
  {
    id: 'bullet',
    label: '글머리 목록',
    hint: '- 항목',
    shortcut: 'ul',
    keywords: ['ul', 'list', 'bullet', '목록', '글머리', '리스트', '불릿'],
    kind: 'line',
    prefix: '- ',
  },
  {
    id: 'ordered',
    label: '번호 목록',
    hint: '1. 항목',
    shortcut: 'ol',
    keywords: ['ol', 'list', 'number', 'ordered', '번호', '숫자', '순서'],
    kind: 'line',
    prefix: '1. ',
  },
  {
    id: 'todo',
    label: '할 일 목록',
    hint: '[] 할 일',
    shortcut: 'todo',
    keywords: ['todo', 'task', 'check', 'checkbox', '할일', '할 일', '체크', '투두'],
    kind: 'line',
    prefix: '- [ ] ',
  },
  {
    id: 'toggle',
    label: '토글',
    hint: '> 접고 펴는 글',
    shortcut: 'toggle',
    keywords: ['toggle', 'details', 'fold', '토글', '접기', '펼치기'],
    kind: 'line',
    prefix: '> ',
  },
  ...[1, 2, 3].map((level) => ({
    id: `toggle-h${level}`,
    label: `제목 토글 ${level}`,
    hint: `${'#'.repeat(level)} 제목을 접고 펴기`,
    shortcut: `th${level}`,
    keywords: [`toggleh${level}`, `toggle${level}`, `제목토글${level}`, '토글제목', 'toggleheading'],
    kind: 'line',
    prefix: '> ',
  })),
  {
    id: 'quote',
    label: '인용',
    hint: '" 인용문',
    shortcut: 'quote',
    keywords: ['quote', 'blockquote', '인용', '따옴표'],
    kind: 'line',
    prefix: '> ',
  },
  {
    id: 'table',
    label: '표',
    hint: '줄·열로 된 표',
    shortcut: 'table',
    keywords: ['table', 'grid', '표', '테이블', '격자'],
    kind: 'block',
  },
  {
    id: 'code',
    label: '코드 블록',
    hint: '```java … ```',
    shortcut: 'code',
    keywords: ['code', 'codeblock', 'pre', '코드', '코드블록', '소스'],
    kind: 'block',
    // {} 자리에 커서가 간다
    template: '```\n{}\n```',
  },
  {
    id: 'image',
    label: '이미지',
    hint: '파일을 골라 올린다',
    shortcut: 'img',
    keywords: ['img', 'image', 'photo', 'picture', '이미지', '사진', '그림', '스크린샷'],
    kind: 'block',
  },
  {
    id: 'divider',
    label: '구분선',
    hint: '---',
    shortcut: 'hr',
    keywords: ['hr', 'divider', 'line', '구분선', '구분', '수평선', '선'],
    kind: 'block',
    template: '---\n{}',
  },
  {
    id: 'tip',
    label: '콜아웃 · 팁',
    hint: ':::tip … :::',
    shortcut: 'tip',
    keywords: ['tip', 'callout', '팁', '콜아웃', '알림'],
    kind: 'block',
    template: ':::tip\n{}\n:::',
  },
  {
    id: 'warning',
    label: '콜아웃 · 주의',
    hint: ':::warning … :::',
    shortcut: 'warn',
    keywords: ['warn', 'warning', 'callout', '주의', '경고', '콜아웃'],
    kind: 'block',
    template: ':::warning\n{}\n:::',
  },
  {
    id: 'note',
    label: '콜아웃 · 참고',
    hint: ':::note … :::',
    shortcut: 'note',
    keywords: ['note', 'callout', '참고', '콜아웃'],
    kind: 'block',
    template: ':::note\n{}\n:::',
  },
  {
    id: 'bold',
    label: '굵게',
    hint: '**굵게**',
    shortcut: 'b',
    keywords: ['b', 'bold', 'strong', '굵게', '강조', '볼드'],
    kind: 'inline',
    template: '**{}**',
    placeholder: '굵게',
  },
  {
    id: 'italic',
    label: '기울임',
    hint: '*기울임*',
    shortcut: 'i',
    keywords: ['i', 'italic', 'em', '기울임', '이탤릭'],
    kind: 'inline',
    template: '*{}*',
    placeholder: '기울임',
  },
  {
    id: 'inline-code',
    label: '인라인 코드',
    hint: '`코드`',
    shortcut: 'c',
    keywords: ['c', 'code', 'inline', '인라인', '코드'],
    kind: 'inline',
    template: '`{}`',
    placeholder: '코드',
  },
  {
    id: 'link',
    label: '링크',
    hint: '[글자](주소)',
    shortcut: 'link',
    keywords: ['link', 'url', 'a', '링크', '주소', '하이퍼링크'],
    kind: 'inline',
    template: '[{}](https://)',
    placeholder: '링크 글자',
  },
];

/**
 * 쳐 넣은 글자로 항목을 거른다. 한글·영문 둘 다 받는다.
 *
 * 더 딱 맞는 항목을 앞에 둔다. Enter 는 맨 앞 항목을 고르므로, /b 를 쳤는데 글자 b 가
 * 들어 있다는 이유로 "글머리 목록" 이 먼저 고르면 안 된다 — 단축 표기가 b 인 "굵게" 가 먼저다.
 */
export function filterSlashCommands(query) {
  // "제목 2" 처럼 띄어 쓴 라벨도 "제목2" 로 찾히게 공백을 지우고 비교한다
  const keyword = normalize(query);

  if (!keyword) {
    return POST_SLASH_COMMANDS;
  }

  return POST_SLASH_COMMANDS
    .map((command, order) => ({ command, order, rank: matchRank(command, keyword) }))
    .filter((item) => item.rank >= 0)
    .sort((a, b) => a.rank - b.rank || a.order - b.order)
    .map((item) => item.command);
}

/** 작을수록 딱 맞는다. 맞지 않으면 -1 */
function matchRank(command, keyword) {
  const shortcut = normalize(command.shortcut);
  const label = normalize(command.label);
  const words = command.keywords.map(normalize);

  if (shortcut === keyword || label === keyword || words.includes(keyword)) {
    return 0;
  }

  if (shortcut.startsWith(keyword) || label.startsWith(keyword) || words.some((word) => word.startsWith(keyword))) {
    return 1;
  }

  if (shortcut.includes(keyword) || label.includes(keyword) || words.some((word) => word.includes(keyword))) {
    return 2;
  }

  return -1;
}

function normalize(text) {
  return String(text ?? '').toLowerCase().replace(/\s+/g, '');
}
