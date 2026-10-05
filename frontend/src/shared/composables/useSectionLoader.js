import { onBeforeUnmount, reactive } from 'vue';

import { readCachedResponse, writeCachedResponse } from '../data/responseCache';

/**
 * 화면 한 장을 영역별로 나눠 불러온다.
 *
 * - 영역마다 따로 요청하고, 먼저 온 영역부터 그린다. 한 요청이 늦다고 화면 전체가 기다리지 않는다.
 * - {@code restart} 뒤나 화면을 떠난 뒤에 도착한 응답은 버린다. 글을 옮겨 다닐 때 앞 글의
 *   늦은 응답이 새 글 위에 덮이는 일을 막는다.
 * - {@code cacheKey} 를 주면 전에 받은 값을 먼저 보여 주고, 새로 받은 값으로 바꾼다.
 *
 * 대문·목록·글 상세가 같은 일을 각자 다르게 하고 있어서 한 곳으로 모았다.
 */
export function useSectionLoader(sectionKeys) {
  const loading = reactive(Object.fromEntries(sectionKeys.map((key) => [key, true])));
  let generation = 0;

  onBeforeUnmount(() => {
    generation += 1;
  });

  /** 화면이 다른 대상을 보게 됐다. 하던 요청의 결과는 받지 않는다 */
  function restart() {
    generation += 1;
    sectionKeys.forEach((key) => {
      loading[key] = true;
    });
  }

  /**
   * 영역 하나를 불러온다.
   *
   * 실패를 알릴 때 {@code showingCached} 를 함께 넘긴다. 들고 있던 값을 이미 보여 주고 있으면
   * 화면을 오류로 바꿀지는 그 화면이 정한다.
   */
  function load(key, { request, apply, cacheKey = null, onError = reportError }) {
    const requested = generation;
    const cached = cacheKey ? readCachedResponse(cacheKey) : undefined;

    if (cached !== undefined) {
      apply(cached);
      loading[key] = false;
    }

    return request()
      .then((value) => {
        if (requested !== generation) return;
        if (cacheKey) writeCachedResponse(cacheKey, value);
        apply(value);
      })
      .catch((error) => {
        if (requested !== generation) return;
        onError(error, { showingCached: cached !== undefined });
      })
      .finally(() => {
        if (requested === generation) loading[key] = false;
      });
  }

  return { loading, load, restart };
}

function reportError(error) {
  console.error(error);
}
