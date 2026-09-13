/**
 * 블록 편집의 되돌리기 기록.
 *
 * 브라우저 기본 되돌리기는 한 칸 안의 글자만 되돌린다. 블록을 나누고 합치고
 * 옮기고 종류를 바꾸는 것은 배열을 바꾸는 일이라 브라우저가 알지 못한다.
 * 그래서 Cmd+Z 를 우리가 받아 여기 쌓아 둔 장면으로 되돌린다.
 *
 * 쌓는 시점은 "바뀐 직후"다. 글자 입력은 값이 바뀐 뒤에야 알림이 오기 때문에
 * 바꾸기 전에 기록할 방법이 없다. 그래서 부르는 쪽이 직전 장면을 들고 있다가 넘긴다.
 */

// 한 글자마다 쌓으면 문장 하나 되돌리는 데 Cmd+Z 를 수십 번 눌러야 한다.
// 같은 자리에 이어 치는 동안은 한 덩어리로 묶는다
const MERGE_MS = 700;

// 한없이 쌓으면 긴 글에서 메모리가 는다. 오래된 것부터 버린다
const LIMIT = 100;

export function createEditorHistory({ limit = LIMIT, mergeMs = MERGE_MS } = {}) {
  const past = [];
  const future = [];

  let lastKey = '';
  let lastAt = 0;

  return {
    /**
     * 방금 바뀌었다고 알린다. entry 는 바뀌기 직전 장면이다.
     *
     * @param mergeKey 같은 값이 이어지는 동안 한 덩어리로 묶는다. 빈 값이면 언제나 따로 쌓는다
     * @returns 실제로 쌓았으면 true, 앞 덩어리에 묶였으면 false
     */
    record(entry, mergeKey = '', now = Date.now()) {
      const merged = mergeKey !== '' && mergeKey === lastKey && now - lastAt < mergeMs;

      lastKey = mergeKey;
      lastAt = now;

      if (merged) {
        return false;
      }

      past.push(entry);

      if (past.length > limit) {
        past.shift();
      }

      // 되돌린 뒤 새로 고치면 앞으로 가기는 갈 곳을 잃는다
      future.length = 0;

      return true;
    },

    /** @returns 돌아갈 장면. 없으면 null */
    undo(current) {
      if (past.length === 0) {
        return null;
      }

      future.push(current);
      // 되돌린 직후에 친 글자가 되돌리기 전 덩어리에 묶이면 안 된다
      lastKey = '';

      return past.pop();
    },

    redo(current) {
      if (future.length === 0) {
        return null;
      }

      past.push(current);
      lastKey = '';

      return future.pop();
    },

    /** 다른 글을 불러왔을 때. 앞 글의 장면으로 되돌아가면 안 된다 */
    reset() {
      past.length = 0;
      future.length = 0;
      lastKey = '';
      lastAt = 0;
    },
  };
}
