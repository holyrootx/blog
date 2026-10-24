import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { getReadingPosition, saveReadingPosition } from '../../member/api/memberReadingApi';
import { SCROLL_OFFSET } from '../../../app/router/scrollToHash';

/**
 * 회원이 글에서 읽던 자리를 남기고, 다시 왔을 때 "이어서 읽기" 로 돌아가게 한다.
 *
 * <p>자리는 화면 좌표가 아니라 "본문 몇 번째 덩어리의 몇 % 지점" 이다. 같은 사람이 휴대폰에서 읽다가
 * PC 로 와도 줄바꿈이 달라 좌표로는 다른 곳을 가리킨다. 덩어리 글자의 지문도 함께 남겨서,
 * 글이 고쳐져 그 덩어리가 사라지면 엉뚱한 곳 대신 처음부터 읽게 한다.</p>
 *
 * <p>스스로 스크롤하지 않는다. 사람이 단추를 눌렀을 때만 옮긴다 — 열자마자 화면이 튀면 어디서
 * 읽기 시작할지 고를 수 없다.</p>
 */
const ROOT_SELECTOR = '[data-reading-root]';
const SAVE_INTERVAL_MS = 15_000;
/** 첫 덩어리의 이 천분율 안쪽은 "아직 읽기 전" 으로 본다. 돌아갈 곳도, 덮어쓸 이유도 없다 */
const START_THRESHOLD = 50;
const FINGERPRINT_TEXT_LIMIT = 500;

export function useReadingPosition({ postId, ready, memberId }) {
  const resume = ref(null);

  let latest = null;
  let lastSent = null;
  let lastSentAt = 0;
  let saveTimer = null;
  let frame = 0;
  let loadGeneration = 0;
  // 이 글의 이어서 읽기를 이미 물어봤는가. 닫은 단추가 다시 뜨지 않게 한다
  let resumeCheckedFor = null;

  watch(
    () => [String(postId.value ?? ''), Boolean(ready.value), memberId.value ?? null],
    ([id, isReady, owner], previous) => {
      const [previousId, , previousOwner] = previous ?? [];
      if (previousOwner === owner && previousId && previousId !== id) {
        // 다른 글로 옮겨 가기 전에 보던 글의 자리를 남긴다. 측정은 이미 해 둔 값을 쓴다
        flush({ keepalive: true });
      }
      if (previousId !== id || previousOwner !== owner || !owner) {
        reset();
      }
      if (id && isReady && owner && resumeCheckedFor !== id) {
        resumeCheckedFor = id;
        loadResume(id);
      }
    },
    { immediate: true, flush: 'sync' },
  );

  onMounted(() => {
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('pagehide', onPageHide);
    document.addEventListener('visibilitychange', onVisibilityChange);
  });

  onBeforeUnmount(() => {
    flush({ keepalive: true });
    window.removeEventListener('scroll', onScroll);
    window.removeEventListener('pagehide', onPageHide);
    document.removeEventListener('visibilitychange', onVisibilityChange);
    cancelAnimationFrame(frame);
    clearTimeout(saveTimer);
    loadGeneration += 1;
  });

  async function loadResume(id) {
    const generation = ++loadGeneration;
    let saved;
    try {
      saved = await getReadingPosition(id);
    } catch (error) {
      // 이어서 읽기는 덤이다. 못 받아 와도 글은 그대로 읽힌다
      console.warn(error);
      return;
    }
    if (generation !== loadGeneration || !saved) return;

    await nextTick();
    if (generation !== loadGeneration) return;

    const index = findBlock(readingBlocks(), saved);
    // 지문이 맞는 덩어리가 없으면 글이 고쳐진 것이다. 처음부터 읽게 둔다
    if (index === -1 || isStart(index, saved.blockOffset)) return;

    resume.value = { ...saved, blockIndex: index };
    lastSent = { postId: id, ...saved };
  }

  function resumeReading() {
    const saved = resume.value;
    if (!saved) return;

    const blocks = readingBlocks();
    // 이미지가 뒤늦게 들어와 자리가 바뀌었을 수 있어서 누른 순간에 다시 찾는다
    const index = findBlock(blocks, saved);
    resume.value = null;
    if (index === -1) return;

    const block = blocks[index];
    const rect = block.getBoundingClientRect();
    const top = window.scrollY + rect.top + (rect.height * saved.blockOffset) / 1000 - SCROLL_OFFSET;
    const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    window.scrollTo({ top: Math.max(0, top), behavior: reduceMotion ? 'auto' : 'smooth' });

    // 화면 낭독기도 같은 곳에서 이어 읽게 초점을 옮긴다. 화면은 위에서 이미 옮겼다
    if (!block.hasAttribute('tabindex')) block.setAttribute('tabindex', '-1');
    block.focus({ preventScroll: true });
  }

  function dismissResume() {
    resume.value = null;
  }

  function onScroll() {
    if (frame) return;
    frame = requestAnimationFrame(() => {
      frame = 0;
      track();
    });
  }

  function track() {
    if (!postId.value || !ready.value || !memberId.value) return;

    const position = measure(String(postId.value));
    if (!position) return;
    latest = { ...position, memberId: memberId.value };

    // 스스로 그 자리까지 내려왔으면 단추는 할 일이 없다
    if (resume.value && position.blockIndex >= resume.value.blockIndex) {
      resume.value = null;
    }

    clearTimeout(saveTimer);
    const wait = Math.max(0, lastSentAt + SAVE_INTERVAL_MS - Date.now());
    saveTimer = setTimeout(() => flush(), wait);
  }

  function onPageHide() {
    flush({ keepalive: true });
  }

  function onVisibilityChange() {
    if (document.visibilityState === 'hidden') flush({ keepalive: true });
  }

  function flush({ keepalive = false } = {}) {
    clearTimeout(saveTimer);
    const position = latest;
    if (!position || position.memberId !== memberId.value
      || isStart(position.blockIndex, position.blockOffset) || samePosition(position, lastSent)) return;

    lastSent = position;
    lastSentAt = Date.now();
    saveReadingPosition(position.postId, position, { keepalive }).catch((error) => {
      // 저장 실패를 알림으로 띄우면 읽는 중에 방해가 된다. 다음 스크롤이나 화면을 떠날 때 다시 보낸다
      if (lastSent === position) lastSent = null;
      console.warn(error);
    });
  }

  function reset() {
    loadGeneration += 1;
    resumeCheckedFor = null;
    cancelAnimationFrame(frame);
    frame = 0;
    clearTimeout(saveTimer);
    resume.value = null;
    latest = null;
    lastSent = null;
    lastSentAt = 0;
  }

  return { resume, resumeReading, dismissResume };
}

/** 본문의 맨 위 덩어리들. 토글 안쪽은 바깥 덩어리 하나로 친다 */
function readingBlocks() {
  const root = document.querySelector(ROOT_SELECTOR);
  return root ? [...root.children] : [];
}

/** 헤더 바로 아래 줄에 걸린 덩어리와 그 안에서의 위치 */
function measure(postId) {
  const blocks = readingBlocks();
  if (blocks.length === 0) return null;

  let blockIndex = blocks.findIndex((block) => block.getBoundingClientRect().bottom > SCROLL_OFFSET);
  // 본문을 다 지나 댓글까지 내려왔으면 마지막 덩어리의 끝이다
  if (blockIndex === -1) blockIndex = blocks.length - 1;

  const rect = blocks[blockIndex].getBoundingClientRect();
  const ratio = rect.height > 0 ? (SCROLL_OFFSET - rect.top) / rect.height : 0;

  return {
    postId,
    blockIndex,
    blockOffset: Math.round(Math.min(1, Math.max(0, ratio)) * 1000),
    blockHash: fingerprint(blocks[blockIndex]),
  };
}

/** 지문이 같은 덩어리 중 저장한 순번에 가장 가까운 것. 앞에 문단이 늘어도 따라간다 */
function findBlock(blocks, saved) {
  let found = -1;
  blocks.forEach((block, index) => {
    if (fingerprint(block) !== saved.blockHash) return;
    if (found === -1 || Math.abs(index - saved.blockIndex) < Math.abs(found - saved.blockIndex)) {
      found = index;
    }
  });
  return found;
}

/**
 * 덩어리 글자로 만든 16진수 8자리(FNV-1a). 글자가 없는 그림은 주소로 대신한다.
 * 보안용이 아니라 "같은 문단인가" 만 가리는 용도라 짧아도 된다.
 */
export function fingerprint(block) {
  const text = (block.textContent ?? '').replace(/\s+/g, ' ').trim().slice(0, FINGERPRINT_TEXT_LIMIT)
    || block.querySelector('img')?.getAttribute('src')
    || block.tagName;

  let hash = 0x811c9dc5;
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash.toString(16).padStart(8, '0');
}

function isStart(blockIndex, blockOffset) {
  return blockIndex === 0 && blockOffset < START_THRESHOLD;
}

function samePosition(left, right) {
  return Boolean(right)
    && left.postId === right.postId
    && left.blockIndex === right.blockIndex
    && left.blockOffset === right.blockOffset
    && left.blockHash === right.blockHash;
}
