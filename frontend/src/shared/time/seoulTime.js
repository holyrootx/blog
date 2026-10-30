/**
 * 시각을 서울 기준으로 적는다.
 *
 * <p>브라우저 시간대에 맡기면 해외에서 본 사람과 국내에서 본 사람이 같은 기록을 다른 날짜로 읽는다.
 * 통계의 날짜 경계도 서울 자정이라, 화면에 찍는 시각도 같은 기준이어야 둘이 맞는다.</p>
 */
const SEOUL = 'Asia/Seoul';

const DATE_TIME_FORMAT = new Intl.DateTimeFormat('ko-KR', {
	timeZone: SEOUL,
	year: 'numeric',
	month: '2-digit',
	day: '2-digit',
	hour: '2-digit',
	minute: '2-digit',
	hourCycle: 'h23',
});

/** 2026.10.17 21:30 */
export function formatSeoulDateTime(value) {
	const parts = toParts(value);

	return parts ? `${parts.year}.${parts.month}.${parts.day} ${parts.hour}:${parts.minute}` : '';
}

/** 2026.10.17 */
export function formatSeoulDate(value) {
	const parts = toParts(value);

	return parts ? `${parts.year}.${parts.month}.${parts.day}` : '';
}

function toParts(value) {
	if (!value) {
		return null;
	}

	const date = new Date(value);

	if (Number.isNaN(date.getTime())) {
		return null;
	}

	return Object.fromEntries(
		DATE_TIME_FORMAT.formatToParts(date)
			.filter((part) => part.type !== 'literal')
			.map((part) => [part.type, part.value]),
	);
}
