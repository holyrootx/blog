<script setup>
// 모든 팝업이 쓰는 한 벌. 관리자·공개 화면 공통이다.
//
// 구조를 여기서 강제한다 — 제목은 항상 맨 위, 닫는 단추는 제목 오른쪽의 ×.
// 팝업마다 손으로 지키게 두면 반드시 빠뜨리는 곳이 생긴다. 실제로 전에는
// 검색창에 제목이 없었고 신고창에는 × 가 없었다.
//
// 푸터 단추는 넘겨받는다. 다만 순서 규칙이 있다 — 닫기·취소가 가장 오른쪽이다.
//
// 작은 폼과 확인창 전용 모달.
//
// ⚠️ 글 편집처럼 "쓰던 내용이 날아가면 곤란한" 화면은 모달로 만들지 않는다.
//    docs/2026-006-admin-screen-spec.md §9 에서 폐기된 결정이다.
//    이유: 검증·보호 로직이 화면과 모달 두 곳으로 갈라지고,
//    ESC·바깥클릭이라는 내용 유실 경로가 새로 생긴다.
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

// 확인창이 폼 모달 위에 겹쳐 뜨는 경우가 있어서 열린 모달을 스택으로 센다.
// 이게 없으면 (1) Esc 가 겹친 모달을 한꺼번에 닫고
// (2) 위 모달만 닫아도 아래가 열려 있는데 배경 스크롤 잠금이 풀린다
import {
	isTopModal,
	modalIndex,
	popModal,
	pushModal,
	topModalPanel,
} from './modalStack';

const props = defineProps({
	open: {
		type: Boolean,
		default: false,
	},
	title: {
		type: String,
		required: true,
	},
	description: {
		type: String,
		default: '',
	},
	// 바깥을 눌렀을 때 닫을지. 입력 중인 폼이면 false 를 준다
	closeOnBackdrop: {
		type: Boolean,
		default: true,
	},
	// 검색창처럼 열자마자 입력할 곳이 정해져 있으면 패널 안의 선택자를 받는다.
	initialFocus: {
		type: String,
		default: '',
	},
	size: {
		type: String,
		default: 'medium', // small | medium | large
	},
	/**
	 * 색만 가른다. 구조(제목·X·본문·푸터)는 어느 쪽이든 같다.
	 *
	 * 관리자와 공개 화면은 바탕색·글꼴 무게가 달라서 한 벌로는 어느 한쪽이 튄다.
	 * 그렇다고 모달을 두 벌 만들면 "제목은 위, 닫기는 X" 같은 규칙이 한쪽에서 빠진다.
	 */
	variant: {
		type: String,
		default: 'public', // public | admin
	},
});

const emit = defineEmits(['close']);

const panelRef = ref(null);
// 이 인스턴스를 스택에서 식별할 표식
const modalId = Symbol('modal');

let returnTarget = null;
let lastFocused = null;
let focusVersion = 0;

function canFocus(element) {
	return element instanceof HTMLElement && element.isConnected
		&& !element.matches(':disabled') && !element.closest('[inert]')
		&& element.getClientRects().length > 0 && getComputedStyle(element).visibility === 'visible';
}

function focusInside() {
	const panel = panelRef.value;
	if (!panel || !props.open || !isTopModal(modalId)) {
		return;
	}
	const preferred = props.initialFocus ? panel.querySelector(props.initialFocus) : null;
	const target = panel.contains(lastFocused) && canFocus(lastFocused) ? lastFocused : preferred;
	(canFocus(target) ? target : panel).focus({ preventScroll: true });
}

function close() {
	if (isTopModal(modalId)) {
		emit('close');
	}
}

function onBackdrop() {
	if (props.closeOnBackdrop) {
		close();
	}
}

function onFocusin(event) {
	if (!props.open || !isTopModal(modalId)) {
		return;
	}
	if (panelRef.value?.contains(event.target)) {
		lastFocused = event.target;
	} else {
		focusInside();
	}
}

function onKeydown(event) {
	if (!props.open || !isTopModal(modalId) || event.defaultPrevented || event.isComposing) {
		return;
	}
	if (event.key === 'Escape') {
		event.preventDefault();
		close();
		return;
	}
	if (event.key !== 'Tab') {
		return;
	}

	// 매번 다시 찾는다. 검색 결과나 조건부 입력칸이 나타나도 현재 화면 순서로 이동한다.
	const controls = [...panelRef.value.querySelectorAll(
		'a[href], area[href], button, input, select, textarea, iframe, [tabindex], [contenteditable="true"]',
	)].filter(element => element.tabIndex >= 0 && canFocus(element))
		.sort((a, b) => (a.tabIndex || Infinity) - (b.tabIndex || Infinity));
	const first = controls[0];
	const last = controls.at(-1);
	const active = document.activeElement;
	if (!first) {
		event.preventDefault();
		panelRef.value.focus({ preventScroll: true });
	} else if (!controls.includes(active) || (event.shiftKey ? active === first : active === last)) {
		event.preventDefault();
		(event.shiftKey ? last : first).focus();
	}
}

function leaveStack() {
	const wasTop = isTopModal(modalId);
	const target = returnTarget;
	const closingPanel = panelRef.value;
	const version = ++focusVersion;
	popModal(modalId);
	lastFocused = null;
	returnTarget = null;
	if (!wasTop) {
		return;
	}

	nextTick(() => {
		if (version !== focusVersion) {
			return;
		}
		const top = topModalPanel();
		const active = document.activeElement;
		// 닫는 사이 다른 화면이 초점을 정했다면 뒤늦게 빼앗지 않는다.
		if (active !== document.body && active?.isConnected && !closingPanel?.contains(active)) {
			return;
		}
		if (canFocus(target) && (!top || top.contains(target))) {
			target.focus({ preventScroll: true });
		} else {
			top?.focus({ preventScroll: true });
		}
	});
}

watch(
	() => props.open,
	(isOpen) => {
		if (!isOpen) {
			leaveStack();
			return;
		}
		returnTarget = document.activeElement;
		lastFocused = null;
		const version = ++focusVersion;
		pushModal(modalId, () => panelRef.value);
		nextTick(() => {
			if (version !== focusVersion || !props.open || !isTopModal(modalId)) {
				return;
			}
			// 신고 사유처럼 자식이 이미 초점을 정했다면 유지한다.
			if (!panelRef.value?.contains(document.activeElement)) {
				focusInside();
			}
		});
	},
	{ immediate: true },
);

onMounted(() => {
	document.addEventListener('keydown', onKeydown);
	document.addEventListener('focusin', onFocusin);
});

onBeforeUnmount(() => {
	document.removeEventListener('keydown', onKeydown);
	document.removeEventListener('focusin', onFocusin);
	leaveStack();
});
</script>

<template>
	<Teleport to="body">
		<div v-if="open" class="modal"
			:class="`modal--${variant}`"
			:style="{ zIndex: 100 + modalIndex(modalId) }"
			:inert="!isTopModal(modalId)"
			:aria-hidden="!isTopModal(modalId) || undefined"
			@click.self="onBackdrop">
			<div
				ref="panelRef"
				class="modal__panel"
				:class="`modal__panel--${size}`"
				role="dialog"
				aria-modal="true"
				:aria-label="title"
				tabindex="-1"
			>
				<header class="modal__header">
					<div>
						<h2 class="modal__title">{{ title }}</h2>
						<p v-if="description" class="modal__description">{{ description }}</p>
					</div>
					<button class="modal__close" type="button" aria-label="닫기" @click="close">
						×
					</button>
				</header>

				<div class="modal__body">
					<slot />
				</div>

				<footer v-if="$slots.footer" class="modal__footer">
					<slot name="footer" />
				</footer>
			</div>
		</div>
	</Teleport>
</template>
