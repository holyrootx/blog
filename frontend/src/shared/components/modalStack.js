import { shallowReactive } from 'vue';

// 인스턴스 사이에 공유한다. 표시 순서와 키보드 처리 순서도 같은 스택을 따른다.
const openModals = shallowReactive([]);
let bodyStyles = null;

function lockScroll() {
	const { style } = document.body;
	bodyStyles = ['overflow', 'padding-right'].map(name => ({
		name, value: style.getPropertyValue(name), priority: style.getPropertyPriority(name),
	}));
	const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
	if (scrollbarWidth > 0) {
		const padding = parseFloat(getComputedStyle(document.body).paddingRight) || 0;
		style.setProperty('padding-right', `${padding + scrollbarWidth}px`);
	}
	style.setProperty('overflow', 'hidden');
}

function unlockScroll() {
	for (const { name, value, priority } of bodyStyles ?? []) {
		if (value) {
			document.body.style.setProperty(name, value, priority);
		} else {
			document.body.style.removeProperty(name);
		}
	}
	bodyStyles = null;
}

export function pushModal(id, getPanel) {
	if (modalIndex(id) !== -1) {
		return;
	}
	if (openModals.length === 0) {
		lockScroll();
	}
	openModals.push({ id, getPanel });
}

export function popModal(id) {
	const index = modalIndex(id);
	if (index === -1) {
		return;
	}
	openModals.splice(index, 1);
	if (openModals.length === 0) {
		unlockScroll();
	}
}

export function modalIndex(id) {
	return openModals.findIndex(modal => modal.id === id);
}

export function isTopModal(id) {
	return openModals.at(-1)?.id === id;
}

export function topModalPanel() {
	return openModals.at(-1)?.getPanel();
}
