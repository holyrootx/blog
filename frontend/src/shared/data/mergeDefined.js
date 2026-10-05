/**
 * 서버가 채워 준 값만 덮어쓴다.
 *
 * 빈 문자열이나 null 을 그대로 덮으면 화면이 준비해 둔 기본값이 지워진다.
 * 서버가 아직 넣지 않은 칸은 기본값으로 둔다.
 */
export function mergeDefined(base, next) {
  return Object.entries(next ?? {}).reduce(
    (result, [key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        result[key] = value;
      }

      return result;
    },
    { ...base },
  );
}
