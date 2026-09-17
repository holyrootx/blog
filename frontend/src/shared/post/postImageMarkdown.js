/**
 * 본문 이미지의 마크다운 표기.
 *
 * 마크다운 이미지 문법에는 폭을 적을 자리가 없어서 title 칸을 쓴다.
 * 문법 위반이 아니라서 다른 뷰어로 옮겨도 깨지지 않는다.
 *
 *   ![캡션](url)                          폭 100%, 왼쪽
 *   ![캡션](url "60%")                    폭만
 *   ![캡션](url "60% center")             폭 + 정렬
 *   ![캡션](url "60% center 1600x1200")   폭 + 정렬 + 원본 크기
 *   ![캡션](url "1600x1200")              원본 크기만
 *
 * 토큰은 자리를 따지지 않고 각각 찾는다. 순서가 틀려도 읽힌다.
 *
 * 원본 크기를 같이 적는 이유는 브라우저에 비율을 미리 알려주려는 것이다.
 * 그래야 이미지가 도착할 때 아래 글이 밀리지 않는다. 업로드 시점에 브라우저가
 * 이미 알고 있는 값이라 따로 치르는 비용이 없다.
 *
 * 편집 화면과 공개 화면이 이 파일 하나만 보게 한다. 같은 정규식을 두 벌 두면
 * 한쪽만 고쳤을 때 이미지가 안 보이는 게 아니라 `![캡션](url "60%")` 이
 * 글자 그대로 문단에 찍힌다. 블록 정규식에서 떨어진 줄은 문단으로 흘러가기 때문이다.
 */

// 폭은 픽셀이 아니라 퍼센트다. 600px 로 저장하면 폰에서 화면보다 넓어진다
export const MIN_IMAGE_WIDTH = 20;
export const MAX_IMAGE_WIDTH = 100;

/**
 * 정렬. 기본이 왼쪽이라 왼쪽은 적지 않는다 — 대부분의 사진이 왼쪽이라
 * 적기 시작하면 본문마다 "left" 가 줄줄이 붙는다.
 *
 * 글이 사진 옆으로 흘러 감싸는 float 이 아니라 블록 정렬이다.
 * 사진은 자기 줄을 온전히 차지하고, 그 줄 안에서 어디에 놓일지만 정한다.
 */
export const IMAGE_ALIGNS = ['left', 'center', 'right'];
export const DEFAULT_IMAGE_ALIGN = 'left';

/** 줄 전체가 이미지일 때만 블록이 된다. 문장에 섞인 이미지는 문단의 인라인 표기로 남는다 */
export const IMAGE_LINE_PATTERN = /^!\[([^\]]*)\]\(([^)\s]+)(?:\s+"([^"]*)")?\)$/;

/** 0 은 "지정 없음"이다. 20~100 밖의 값은 범위 안으로 당긴다 */
export function clampImageWidth(value) {
  const width = Math.round(Number(value));

  if (!Number.isFinite(width) || width <= 0) {
    return 0;
  }

  return Math.min(Math.max(width, MIN_IMAGE_WIDTH), MAX_IMAGE_WIDTH);
}

/**
 * title 칸을 읽는다.
 *
 * 손으로 쓴 글이 들어올 수 있으니 못 읽는 값은 버리고 넘어간다 —
 * 줄 하나가 통째로 안 보이는 것보다 폭 하나를 잃는 편이 낫다.
 */
export function parseImageTitle(title) {
  const source = String(title ?? '');
  const percent = source.match(/(\d{1,3})\s*%/);
  const natural = source.match(/(\d{1,5})\s*[x×]\s*(\d{1,5})/i);
  const align = source.match(/\b(left|center|right)\b/i);

  return {
    width: percent ? clampImageWidth(percent[1]) : 0,
    align: align ? align[1].toLowerCase() : DEFAULT_IMAGE_ALIGN,
    naturalWidth: natural ? Number(natural[1]) : 0,
    naturalHeight: natural ? Number(natural[2]) : 0,
  };
}

/**
 * 적을 것이 없으면 빈 문자열이다. 그래야 폭을 건드린 적 없는 예전 글이
 * 열었다 저장만 해도 원문 그대로 남는다.
 *
 * 100% 는 기본값이라 적지 않는다. 적으면 글마다 의미 없는 "100%" 가 붙는다.
 */
export function formatImageTitle({
  width = 0,
  align = DEFAULT_IMAGE_ALIGN,
  naturalWidth = 0,
  naturalHeight = 0,
} = {}) {
  const parts = [];
  const clamped = clampImageWidth(width);

  if (clamped > 0 && clamped < MAX_IMAGE_WIDTH) {
    parts.push(`${clamped}%`);
  }

  if (IMAGE_ALIGNS.includes(align) && align !== DEFAULT_IMAGE_ALIGN) {
    parts.push(align);
  }

  if (naturalWidth > 0 && naturalHeight > 0) {
    parts.push(`${naturalWidth}x${naturalHeight}`);
  }

  return parts.join(' ');
}
