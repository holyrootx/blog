export const postMock = {
  // 헤더 내비. 실제로는 GET /api/v1/blog/categories 응답
  categories: [
    { id: 1, name: '개발' },
    { id: 2, name: '장비' },
    { id: 3, name: '생각' },
    { id: 4, name: '생활' },
  ],
  post: {
    id: 1,
    category: '개발',
    title: '처음으로 나만의 사이드 프로젝트 배포하기까지',
    excerpt:
      '로컬에서만 돌던 코드를 처음으로 남에게 보여준 날의 기록. 막힌 지점과, 그때 내가 무엇을 몰랐는지 적어둡니다.',
    publishedAt: '2026. 08. 13',
    views: '1,284',
    coverImageUrl: '/images/blog-hero-workspace.png',
    coverImageAlt: '작업 책상 사진',
    tags: ['배포', '도커', '기록'],
    commentCount: 14,
  },
  author: {
    name: '정성주의 기록',
    job: '20대 개발자',
    avatarImageUrl: '/images/blog-hero-workspace.png',
  },
  body: [
    {
      type: 'paragraph',
      text: '배포라는 단어가 오래 무서웠습니다. 코드는 어떻게든 굴러가는데, 그걸 인터넷 위에 올려두는 일은 완전히 다른 종류의 일처럼 보였거든요. 이번 주말에 드디어 그 벽을 한 번 넘어봤습니다.',
    },
    { type: 'heading', id: 'section-1', text: '1. 일단 제일 작게 잘라내기' },
    {
      type: 'paragraph',
      text: '기능을 다 만들고 올리려고 하면 영원히 못 올립니다. 그래서 “글 하나를 저장하고 다시 읽어오는 것”까지만 남기고 전부 지웠습니다.',
    },
    {
      type: 'quote',
      text: '완성해서 올리는 게 아니라, 올릴 수 있는 크기로 줄이는 게 먼저였다.',
    },
    // 마크다운 :::tip / :::warning / :::note 을 렌더링한 결과
    {
      type: 'callout',
      variant: 'tip',
      text: '기능을 쪼갤 때는 “화면 하나”가 아니라 “저장하고 다시 읽어오기”처럼 끝까지 도는 한 줄기로 자르는 게 좋습니다.',
    },
    {
      type: 'code',
      caption: '$ 배포 스크립트',
      lines: [
        { accent: 'npm', text: ' run build' },
        { accent: 'docker', text: ' compose up -d --build' },
      ],
      note: '# 로그가 멈추면 성공이 아니라, 대개 실패입니다',
    },
    { type: 'ad', label: '본문 중간 배너 — 728 × 90' },
    { type: 'heading', id: 'section-2', text: '2. 환경변수와 포트' },
    {
      type: 'paragraph',
      text: '로컬에서는 아무 문제 없던 설정이 서버에서는 전부 비어 있었습니다. 환경변수를 파일에 적어두는 것과, 실행되는 프로세스가 그 값을 읽는 것은 다른 이야기였습니다.',
    },
    {
      type: 'callout',
      variant: 'warning',
      text: '.env 파일을 만들어두는 것만으로는 아무 일도 일어나지 않습니다. 그 값을 실제로 읽어가는 설정이 있는지 먼저 확인하세요.',
    },
    { type: 'heading', id: 'section-3', text: '3. 다음에 해볼 것' },
    {
      type: 'paragraph',
      text: '한 번 올려두고 나니 고칠 것이 더 잘 보입니다. 다음에는 배포 스크립트를 손으로 실행하지 않도록 만들어볼 생각입니다.',
    },
  ],
  toc: [
    { id: 'section-1', text: '1. 일단 제일 작게 잘라내기' },
    { id: 'section-2', text: '2. 환경변수와 포트' },
    { id: 'section-3', text: '3. 다음에 해볼 것' },
  ],
  adjacentPosts: {
    prev: { id: 2, title: 'ESP32로 책상 온습도 모니터링 만들기' },
    next: { id: 3, title: '첫 월급을 받고 고민한 것들' },
  },
  relatedPosts: [
    {
      id: 2,
      category: '장비',
      title: 'ESP32로 책상 온습도 모니터링 만들기',
      publishedAt: '2026. 08. 12',
      views: '1,102',
      imageUrl: '/images/blog-hero-workspace.png',
    },
    {
      id: 3,
      category: '생각',
      title: '첫 월급을 받고 고민한 것들',
      publishedAt: '2026. 08. 10',
      views: '982',
      imageUrl: '/images/blog-hero-workspace.png',
    },
    {
      id: 4,
      category: '생활',
      title: '번아웃이 온 것 같을 때, 내가 다시 시작하는 방법',
      publishedAt: '2026. 08. 08',
      views: '992',
      imageUrl: '/images/blog-hero-workspace.png',
    },
  ],
  asideAds: [
    { label: '사이드 광고 — 300 × 250', height: 250 },
    { label: '사이드 광고 — 300 × 600', height: 520, sticky: true },
  ],
  // 모바일에서만 화면 하단에 붙는 앵커 배너 (시안 2a)
  anchorAd: { label: '앵커 배너 — 320 × 50' },
  // 정렬은 최신순 고정(공감순 없음), 페이지네이션 대신 커서 기반 무한 스크롤.
  comments: {
    total: 13,
    placeholder: '따뜻한 댓글 하나가 다음 글을 쓰게 만듭니다.',
    maxLength: 1000,
    items: [
      {
        id: 14,
        author: '유진',
        createdAt: '2026. 08. 13 21:04',
        content:
          '저도 배포 앞에서 계속 미루고만 있었는데, “올릴 수 있는 크기로 줄인다”는 말이 오래 남을 것 같아요. 환경변수 편도 기다립니다!',
        isSecret: false,
        deleted: false,
        hiddenReplyCount: 0,
        replies: [
          {
            id: 13,
            author: '정성주의 기록',
            createdAt: '2026. 08. 13 22:10',
            content: '환경변수 편은 이번 주에 올려볼게요. 저도 그 부분에서 두 시간을 날렸습니다.',
            isAuthor: true,
            deleted: false,
          },
        ],
      },
      {
        id: 12,
        author: '문어',
        createdAt: '2026. 08. 13 18:47',
        content:
          '도커 컴포즈 설정 파일도 같이 볼 수 있을까요? 온습도 모니터링 글부터 정주행 중입니다.',
        isSecret: false,
        deleted: false,
        hiddenReplyCount: 3,
        replies: [],
      },
      {
        id: 11,
        author: '',
        createdAt: '2026. 08. 13 11:20',
        content: '',
        isSecret: false,
        deleted: true,
        hiddenReplyCount: 0,
        replies: [
          {
            id: 10,
            author: '정성주의 기록',
            createdAt: '2026. 08. 13 12:02',
            content: '알려주셔서 감사합니다. 본문에 반영했습니다.',
            isAuthor: true,
            deleted: false,
          },
        ],
      },
    ],
    nextCursor: 11,
    hasNext: true,
    bottomAd: { label: '댓글 하단 배너 — 728 × 90' },
  },

  // 스케치 전용 — 무한 스크롤로 이어서 불러오는 다음 묶음 (실제로는 API가 내려줌)
  nextCommentPage: {
    items: [
      {
        id: 8,
        author: '해달',
        createdAt: '2026. 08. 11 20:15',
        content: '저는 아직 로컬에서만 굴리고 있는데, 이번 주말에 따라 해봐야겠어요.',
        isSecret: false,
        deleted: false,
        hiddenReplyCount: 0,
        replies: [],
      },
      {
        id: 7,
        author: '민서',
        createdAt: '2026. 08. 11 08:42',
        content: '포트 충돌 얘기가 제일 공감됐습니다. 저도 거기서 반나절 썼어요.',
        isSecret: false,
        deleted: false,
        hiddenReplyCount: 0,
        replies: [],
      },
    ],
    nextCursor: 7,
    hasNext: false,
  },
};
