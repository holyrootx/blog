<script setup>
import { computed, ref } from 'vue';

/**
 * 날짜별 조회 기록 막대.
 *
 * <p>한 가지 값만 그리므로 색은 하나다. 범례 대신 제목이 무엇을 그렸는지 말한다.
 * 막대마다 숫자를 붙이면 30일 그래프가 숫자로 덮이므로 가장 높은 날에만 붙이고,
 * 나머지는 마우스를 올리거나 키보드로 옮겨 가면 보이게 한다. 같은 숫자는 아래 표에도 있다.</p>
 */
const props = defineProps({
	// [{ date: 'YYYY-MM-DD'(서울), pageViews, memberViews, anonymousViews }]
	days: {
		type: Array,
		required: true,
	},
});

const activeIndex = ref(null);

const scaleMax = computed(() => niceMax(Math.max(0, ...props.days.map((day) => day.pageViews))));

const peakIndex = computed(() => props.days.reduce(
	(best, day, index) => (day.pageViews > 0 && (best === -1 || day.pageViews >= props.days[best].pageViews)
		? index
		: best),
	-1,
));

const activeDay = computed(() => (activeIndex.value === null ? null : props.days[activeIndex.value]));

// 끝 막대의 말풍선이 그래프 밖으로 나가지 않게 가운데 쪽으로 붙잡는다
const tooltipStyle = computed(() => {
	const percent = ((activeIndex.value + 0.5) / props.days.length) * 100;
	return { left: `clamp(72px, ${percent}%, calc(100% - 72px))` };
});

function barHeight(day) {
	return `${(day.pageViews / scaleMax.value) * 100}%`;
}

// 30일은 날짜를 다 적으면 겹친다. 5일마다와 마지막 날만 적는다
function showsDateLabel(index) {
	return props.days.length <= 7 || index % 5 === 0 || index === props.days.length - 1;
}

function formatDay(date) {
	const [, month, day] = String(date).split('-');
	return `${Number(month)}.${Number(day)}`;
}

function formatCount(value) {
	return value.toLocaleString('ko-KR');
}

/** 축 위쪽 눈금. 읽기 쉬운 값(1·1.5·2·2.5·3·4·5·6·8 의 10배수) 중 가장 가까운 위 값을 쓴다 */
function niceMax(value) {
	if (value <= 4) {
		return 4;
	}
	const power = 10 ** Math.floor(Math.log10(value));
	const step = [1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10].find((candidate) => candidate * power >= value);
	return step * power;
}
</script>

<template>
	<figure class="admin-daily-chart">
		<div class="admin-daily-chart__plot">
			<span class="admin-daily-chart__tick admin-daily-chart__tick--top" aria-hidden="true">
				{{ formatCount(scaleMax) }}
			</span>
			<span class="admin-daily-chart__tick admin-daily-chart__tick--zero" aria-hidden="true">0</span>

			<ol class="admin-daily-chart__columns" :style="{ '--columns': days.length }" aria-label="날짜별 조회 기록">
				<li
					v-for="(day, index) in days"
					:key="day.date"
					class="admin-daily-chart__column"
					:class="{ 'admin-daily-chart__column--active': activeIndex === index }"
					tabindex="0"
					:aria-label="`${formatDay(day.date)} 조회 기록 ${formatCount(day.pageViews)}건, 회원 ${formatCount(day.memberViews)}건, 비회원 ${formatCount(day.anonymousViews)}건`"
					@pointerenter="activeIndex = index"
					@pointerleave="activeIndex = null"
					@focus="activeIndex = index"
					@blur="activeIndex = null"
				>
					<!-- 숫자를 막대 꼭대기에 붙이려고 막대와 같은 높이의 틀 안에 둔다 -->
					<span class="admin-daily-chart__stack" :style="{ height: barHeight(day) }" aria-hidden="true">
						<span v-if="index === peakIndex" class="admin-daily-chart__value">{{ formatCount(day.pageViews) }}</span>
						<span class="admin-daily-chart__bar"></span>
					</span>
				</li>
			</ol>

			<div v-if="activeDay" class="admin-daily-chart__tooltip" :style="tooltipStyle" aria-hidden="true">
				<strong>{{ formatCount(activeDay.pageViews) }}건</strong>
				<span>{{ formatDay(activeDay.date) }}</span>
				<span>회원 {{ formatCount(activeDay.memberViews) }} · 비회원 {{ formatCount(activeDay.anonymousViews) }}</span>
			</div>
		</div>

		<ol class="admin-daily-chart__axis" :style="{ '--columns': days.length }" aria-hidden="true">
			<li v-for="(day, index) in days" :key="day.date">{{ showsDateLabel(index) ? formatDay(day.date) : '' }}</li>
		</ol>

		<details class="admin-daily-chart__table">
			<summary>날짜별 숫자 보기</summary>
			<table>
				<thead>
					<tr>
						<th scope="col">날짜</th>
						<th scope="col">조회 기록</th>
						<th scope="col">회원</th>
						<th scope="col">비회원</th>
					</tr>
				</thead>
				<tbody>
					<tr v-for="day in days" :key="day.date">
						<th scope="row">{{ formatDay(day.date) }}</th>
						<td>{{ formatCount(day.pageViews) }}</td>
						<td>{{ formatCount(day.memberViews) }}</td>
						<td>{{ formatCount(day.anonymousViews) }}</td>
					</tr>
				</tbody>
			</table>
		</details>
	</figure>
</template>
