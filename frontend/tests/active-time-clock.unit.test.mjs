import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createActiveTimeClock, ACTIVE_TIME_LIMIT_MS } from '../src/features/post/composables/activeTimeClock.js';

test('foreground time is cumulative and background time is excluded', () => {
	let now = 0;
	const clock = createActiveTimeClock(() => now);
	assert.equal(clock.hasMeasured(), false);
	clock.resume();
	now = 15_000;
	assert.equal(clock.sample(), 15_000);
	assert.equal(clock.sample(), 15_000);
	clock.pause();
	now = 600_000;
	assert.equal(clock.sample(), 15_000);
	clock.resume();
	now += 10_000;
	assert.equal(clock.sample(), 25_000);
});

test('one minute without input stops measurement and new input does not count the idle gap', () => {
	let now = 0;
	const clock = createActiveTimeClock(() => now);
	clock.resume();
	now = 180_000;
	assert.equal(clock.sample(), 60_000);
	clock.interact();
	now += 10_000;
	assert.equal(clock.sample(), 70_000);
	clock.pause();
	now += 20_000;
	clock.interact();
	assert.equal(clock.sample(), 70_000);
});

test('continued interaction extends the active window but total measurement is bounded', () => {
	let now = 0;
	const clock = createActiveTimeClock(() => now);
	clock.resume();
	for (let i = 0; i < 300; i += 1) {
		now += 30_000;
		clock.interact();
	}
	assert.equal(clock.sample(), ACTIVE_TIME_LIMIT_MS);
});
