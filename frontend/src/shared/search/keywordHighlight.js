/**
 * 검색어와 맞는 자리를 찾아 글을 토막으로 나눈다.
 *
 * <p>왜 직접 나누는가 — 찾은 자리를 굵게 칠하려면 HTML 을 만들어 넣는 길이 가장 짧지만,
 * 그러려면 글 제목을 {@code v-html} 로 그려야 한다. 제목은 관리자가 쓰는 값이라 지금은
 * 안전하더라도, 남이 쓴 글자를 태그로 해석하는 길을 화면에 하나 열어 두는 셈이다.
 * 토막으로 넘기면 Vue 가 알아서 글자로만 그린다.</p>
 *
 * @returns {{text: string, hit: boolean}[]} 순서대로 이어 붙이면 원문이 된다
 */
export function splitByKeyword(text, keyword) {
  const source = text ?? '';

  if (!keyword) {
    return [{ text: source, hit: false }];
  }

  const pieces = [];
  let from = 0;

  // 서버가 대소문자를 가려 찾으므로 여기서도 그대로 비교한다.
  // 한쪽만 느슨하면 걸리지도 않은 글자에 형광펜이 그어진다
  for (;;) {
    const found = source.indexOf(keyword, from);

    if (found === -1) {
      break;
    }

    if (found > from) {
      pieces.push({ text: source.slice(from, found), hit: false });
    }

    pieces.push({ text: source.slice(found, found + keyword.length), hit: true });
    from = found + keyword.length;
  }

  if (from < source.length) {
    pieces.push({ text: source.slice(from), hit: false });
  }

  return pieces.length > 0 ? pieces : [{ text: source, hit: false }];
}

/**
 * 조합 중인 한글인가.
 *
 * <p>한글을 칠 때 "스프링" 은 ㅅ → 스 → 스ㅍ → 스프 → 스프ㄹ → 스프리 → 스프링 을 거친다.
 * 가운데의 {@code 스프ㄹ} 같은 상태로 검색하면 반드시 0건이 나와서, 치는 동안 결과가
 * 자꾸 비었다 찼다 한다. 낱자로 끝나는 말은 아직 덜 친 것으로 보고 넘긴다.</p>
 *
 * <p>U+3131~U+318E 는 낱자(ㄱ, ㅏ …)가 모인 구역이다. 완성된 글자(가, 힣)는 U+AC00 부터라
 * 여기에 걸리지 않는다.</p>
 */
export function endsWithIncompleteHangul(keyword) {
  if (!keyword) {
    return false;
  }

  const last = keyword.charCodeAt(keyword.length - 1);

  return last >= 0x3131 && last <= 0x318e;
}
