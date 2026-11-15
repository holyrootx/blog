export const ACTIVE_TIME_IDLE_MS = 60_000;
export const ACTIVE_TIME_LIMIT_MS = 7_200_000;

/** 화면 활성 구간의 누적값. 입력 내용이나 화면 좌표는 수집하지 않는다. */
export function createActiveTimeClock(now = () => performance.now()) {
	let foreground = false;
	let lastInput = now();
	let lastTick = lastInput;
	let total = 0;
	let measured = false;

	function sample() {
		const current = now();
		if (foreground) {
			const until = Math.min(current, lastInput + ACTIVE_TIME_IDLE_MS);
			total = Math.min(ACTIVE_TIME_LIMIT_MS, total + Math.max(0, until - lastTick));
		}
		lastTick = current;
		return Math.floor(total);
	}

	return {
		sample,
		hasMeasured: () => measured,
		resume() {
			sample();
			foreground = true;
			measured = true;
			lastInput = now();
		},
		pause() {
			sample();
			foreground = false;
		},
		interact() {
			sample();
			if (foreground) {
				lastInput = now();
			}
		},
	};
}
