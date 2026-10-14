import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import vm from 'node:vm';
import { computed, reactive, ref } from 'vue';

const source = (await readFile(new URL('../src/features/admin/pages/AdminHomeSettingsPage.vue', import.meta.url), 'utf8'))
  .match(/<script setup>([\s\S]*?)<\/script>/)[1]
  .replace(/^import[\s\S]*?from ['"][^'"]+['"];\s*/gm, '');
const clone = (value) => JSON.parse(JSON.stringify(value));
const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};
const initial = {
  profile: { id: 1, name: '관리자', intro: '프로필 소개', job: '개발자', avatarImageUrl: '', githubUrl: '', email: '', blogStartedAt: '' },
  hero: { id: 1, subTitle: '기록', title: '대문 제목', intro: '대문 소개', heroImageUrl: '' },
  section: { id: 1, title: '이야기', intro: '영역 소개', noteBadge: '기록 중', note: '안내 문구' },
  topics: [{ id: 10, label: '개발', title: '기존 이야기', description: '기존 설명', keywords: ['Vue'] }],
};

async function fixture() {
  const calls = { profile: [], hero: [], topics: [] }, successes = [], failures = [], dismissed = [];
  const request = (kind) => (payload) => {
    const pending = { ...deferred(), payload: clone(payload) };
    calls[kind].push(pending);
    return pending.promise;
  };
  const page = vm.runInNewContext(`${source}\n;({profile,hero,topicSection,topics,savedSnapshot,hasChanges,saving,busy,formError,loadSettings,saveSettings,addTopic})`, {
    computed, reactive, ref, onMounted() {}, console: { error() {} },
    notifySuccess: (message) => successes.push(message), notifyError: (message) => failures.push(message), dismissToast: (id) => dismissed.push(id),
    getAdminBlogProfile: async () => clone(initial.profile),
    getAdminHomePageHero: async () => clone(initial.hero),
    getAdminHomePageTopicSection: async () => clone(initial.section),
    getAdminHomePageTopics: async () => clone(initial.topics),
    updateAdminBlogProfile: request('profile'), updateAdminHomePageHero: request('hero'), updateAdminHomePageTopics: request('topics'),
  });
  await page.loadSettings();
  const succeed = (kind, index = calls[kind].length - 1) => {
    const call = calls[kind][index], payload = clone(call.payload);
    if (kind === 'topics') {
      const { sectionId, topics, ...section } = payload;
      call.resolve({ section: { id: sectionId, ...section }, topics: topics.map((topic, i) => ({ ...topic, id: topic.id ?? 100 + i })) });
    } else call.resolve(payload);
  };
  const succeedAll = () => Object.keys(calls).forEach((kind) => succeed(kind));
  return { page, calls, successes, failures, dismissed, succeed, succeedAll };
}

for (const kind of ['profile', 'hero']) {
  test(`${kind}: 저장 중 추가 입력은 남고, 실제로 보낸 값까지만 저장 완료로 기록한다`, async () => {
    const f = await fixture(), field = kind === 'profile' ? 'name' : 'title';
    f.page[kind][field] = '먼저 보낸 값';
    const saving = f.page.saveSettings();
    f.page[kind][field] = '저장 중 추가 입력';
    f.succeedAll();
    await saving;
    assert.equal(f.page[kind][field], '저장 중 추가 입력');
    assert.equal(JSON.parse(f.page.savedSnapshot.value)[kind][field], '먼저 보낸 값');
    assert.equal(f.page.hasChanges.value, true);
    assert.match(f.successes[0], /한 번 더 저장/);
    const retry = f.page.saveSettings();
    assert.equal(f.calls[kind][1].payload[field], '저장 중 추가 입력');
    f.succeedAll();
    await retry;
    assert.equal(f.page.hasChanges.value, false);
  });
}

test('이야기 저장 응답은 추가 입력, 입력칸의 객체와 key를 보존하고 새 ID만 연결한다', async () => {
  const f = await fixture();
  f.page.addTopic();
  const topic = f.page.topics.value[1], key = topic.key;
  Object.assign(topic, { label: '새 분류', title: '새 이야기', description: '새 설명', keywordsText: 'Java, Vue' });
  f.page.topicSection.title = '보낸 영역 제목';
  const saving = f.page.saveSettings();
  f.page.topicSection.title = '입력 중인 영역 제목';
  Object.assign(topic, { label: '추가 분류', title: '추가 제목', description: '추가 설명', keywordsText: 'Java, Vue, Spring' });
  f.succeedAll();
  await saving;
  assert.equal(f.page.topicSection.title, '입력 중인 영역 제목');
  assert.equal(f.page.topics.value[1], topic);
  assert.equal(topic.key, key);
  assert.equal(topic.id, 101);
  assert.equal(topic.label, '추가 분류');
  assert.equal(topic.title, '추가 제목');
  assert.equal(topic.description, '추가 설명');
  assert.equal(topic.keywordsText, 'Java, Vue, Spring');
  assert.equal(f.page.hasChanges.value, true);
  const saved = JSON.parse(f.page.savedSnapshot.value).topicSettings;
  assert.equal(saved.title, '보낸 영역 제목');
  assert.equal(saved.topics[1].title, '새 이야기');
  assert.equal(saved.topics[1].id, 101);
  const retry = f.page.saveSettings();
  assert.equal(f.calls.topics[1].payload.topics[1].id, 101);
  assert.equal(f.calls.topics[1].payload.topics[1].title, '추가 제목');
  f.succeedAll();
  await retry;
  assert.equal(f.page.hasChanges.value, false);
});

test('일부 요청이 먼저 실패해도 나머지 응답 전에는 저장을 해제하거나 재요청하지 않는다', async () => {
  const f = await fixture();
  f.page.hero.title = '저장 요청';
  const saving = f.page.saveSettings();
  f.calls.profile[0].reject(new Error('프로필 저장 실패'));
  await new Promise(setImmediate);
  const duplicate = f.page.saveSettings();
  assert.equal(f.page.saving.value, true);
  assert.equal(f.page.busy.value, true);
  assert.equal(f.calls.hero.length, 1);
  assert.equal(f.failures.length, 0);
  await duplicate;
  f.succeed('hero'); f.succeed('topics');
  await saving;
  assert.equal(f.page.saving.value, false);
  assert.equal(f.page.hasChanges.value, true);
  assert.equal(f.successes.length, 0);
  assert.match(f.failures[0], /프로필/);
});

test('다른 영역의 저장이 실패해도 성공한 새 이야기의 ID를 반영하여 재저장한다', async () => {
  const f = await fixture();
  f.page.addTopic();
  const topic = f.page.topics.value[1];
  Object.assign(topic, { label: '추가', title: '첫 제목', description: '첫 설명' });
  const saving = f.page.saveSettings();
  topic.title = '추가로 쓴 제목';
  f.calls.profile[0].reject(new Error('프로필 오류'));
  f.succeed('hero'); f.succeed('topics');
  await saving;
  assert.equal(topic.id, 101);
  assert.equal(topic.title, '추가로 쓴 제목');
  assert.equal(JSON.parse(f.page.savedSnapshot.value).topicSettings.topics[1].id, 101);
  const retry = f.page.saveSettings();
  assert.equal(f.calls.topics[1].payload.topics[1].id, 101);
  assert.equal(f.calls.topics[1].payload.topics[1].title, '추가로 쓴 제목');
  assert.deepEqual(f.dismissed, [1]);
  f.succeedAll();
  await retry;
  assert.equal(f.page.hasChanges.value, false);
  assert.equal(f.page.formError.value, '');
});

test('이야기 저장 실패 시 입력을 유지하고 성공한 대문·프로필만 저장 기준을 갱신한다', async () => {
  const f = await fixture();
  f.page.hero.title = '저장한 대문'; f.page.profile.name = '저장한 이름'; f.page.topics.value[0].title = '보낸 이야기';
  const saving = f.page.saveSettings();
  f.page.topics.value[0].title = '추가 이야기';
  f.succeed('profile'); f.succeed('hero'); f.calls.topics[0].reject(new Error('이야기 저장 실패'));
  await saving;
  const saved = JSON.parse(f.page.savedSnapshot.value);
  assert.equal(saved.hero.title, '저장한 대문');
  assert.equal(saved.profile.name, '저장한 이름');
  assert.equal(saved.topicSettings.topics[0].title, '기존 이야기');
  assert.equal(f.page.topics.value[0].title, '추가 이야기');
  assert.equal(f.page.hasChanges.value, true);
  assert.equal(f.successes.length, 0);
});

test('저장 중 원래 값으로 되돌려도 서버에는 보낸 값이 저장되므로 다시 저장할 수 있다', async () => {
  const f = await fixture();
  f.page.hero.title = '서버로 보낸 제목';
  const saving = f.page.saveSettings();
  f.page.hero.title = initial.hero.title;
  f.succeedAll();
  await saving;
  assert.equal(f.page.hero.title, initial.hero.title);
  assert.equal(f.page.hasChanges.value, true);
});

test('실패 후 화면을 이전 값으로 돌려도 저장 여부가 불확실하면 재시도할 수 있다', async () => {
  const f = await fixture();
  f.page.hero.title = '서버로 보낸 제목';
  const saving = f.page.saveSettings();
  f.page.hero.title = initial.hero.title;
  for (const calls of Object.values(f.calls)) calls[0].reject(new Error('연결 오류'));
  await saving;
  assert.equal(f.page.hasChanges.value, true);
  assert.equal(f.page.saving.value, false);
  assert.equal(f.successes.length, 0);
  const retry = f.page.saveSettings();
  assert.equal(f.calls.hero[1].payload.title, initial.hero.title);
  f.succeedAll();
  await retry;
  assert.equal(f.page.hasChanges.value, false);
});

test('추가 입력 없이 성공하면 공백과 빈 선택값 때문에 미저장 상태가 남지 않는다', async () => {
  const f = await fixture();
  f.page.hero.title = '  저장할 제목  '; f.page.topics.value[0].keywordsText = 'Vue, Vue, Java';
  const saving = f.page.saveSettings();
  f.succeedAll();
  await saving;
  assert.equal(f.page.hasChanges.value, false);
  assert.equal(f.page.saving.value, false);
  assert.equal(f.page.formError.value, '');
  assert.equal(f.successes[0], '대문 설정을 저장했습니다.');
  await f.page.saveSettings();
  assert.equal(f.calls.hero.length, 1);
});

test('저장 중 비워 둔 이야기 입력도 응답으로 덮지 않고 다음 저장에서 검증한다', async () => {
  const f = await fixture();
  f.page.topics.value[0].title = '보낸 제목';
  const saving = f.page.saveSettings();
  f.page.topics.value[0].title = '';
  f.succeedAll();
  await saving;
  assert.equal(f.page.topics.value[0].title, '');
  assert.equal(f.page.hasChanges.value, true);
  await f.page.saveSettings();
  assert.match(f.page.formError.value, /제목과 설명/);
  assert.equal(f.calls.topics.length, 1);
});
