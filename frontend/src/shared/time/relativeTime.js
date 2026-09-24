/**
 * "얼마나 지났는가" 로 시각을 적는다.
 *
 * <p>알림에서 궁금한 것은 몇 시 몇 분인가가 아니라 새 것인가다. {@code 19:37} 을 보면
 * 지금과 견줘 봐야 알지만 {@code 3분 전} 은 그 자리에서 안다.</p>
 *
 * <p>오래된 것까지 상대로 적지는 않는다. {@code 43일 전} 은 도리어 읽기 어렵고,
 * 그쯤 되면 "새 것인가" 가 아니라 "언제 일이었나" 가 궁금해진다. 이레가 넘으면 날짜로 적는다.</p>
 */
const MINUTE = 60 * 1000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** 날짜로 넘어가는 경계. 이 안쪽은 상대, 바깥은 년월일 */
const RELATIVE_LIMIT_DAYS = 7;

export function formatRelativeTime(value, now = Date.now()) {
  const time = toTime(value);

  if (time === null) {
    return '';
  }

  const elapsed = now - time;

  // 시계가 어긋나면 미래로 계산될 수 있다. "-2분 전" 을 보여주느니 방금으로 둔다
  if (elapsed < MINUTE) {
    return '방금';
  }

  if (elapsed < HOUR) {
    return `${Math.floor(elapsed / MINUTE)}분 전`;
  }

  if (elapsed < DAY) {
    return `${Math.floor(elapsed / HOUR)}시간 전`;
  }

  const days = Math.floor(elapsed / DAY);

  if (days === 1) {
    return '어제';
  }

  if (days < RELATIVE_LIMIT_DAYS) {
    return `${days}일 전`;
  }

  return formatDateTime(value, { year: 'numeric', month: '2-digit', day: '2-digit' });
}

/** 마우스를 올렸을 때 보여 줄 정확한 시각 */
export function formatExactTime(value) {
  return formatDateTime(value, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

function formatDateTime(value, options) {
  const time = toTime(value);

  if (time === null) {
    return '';
  }

  // 글 목록의 날짜 표기와 같은 모양이 되게 맞춘다 (2026. 09. 24)
  return new Intl.DateTimeFormat('ko-KR', options).format(new Date(time)).replace(/\.$/, '');
}

function toTime(value) {
  if (!value) {
    return null;
  }

  const time = new Date(value).getTime();

  return Number.isNaN(time) ? null : time;
}
