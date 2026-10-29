/**
 * Google 애널리틱스(GA4) 페이지 기록.
 *
 * 관리자 대시보드의 조회 통계는 어떤 글이 얼마나 읽혔는지를 본다. 이건 방문자가 어디서 왔는지를 본다 —
 * 검색엔진·링크 같은 유입 경로, 그리고 서치 콘솔을 연결하면 구글 검색어까지.
 *
 * 운영 주소에서만 켠다. 로컬·테스트 화면의 방문이 섞이면 통계가 틀어진다.
 * 관리자 화면과 로그인 콜백 화면은 보내지 않는다 — 운영자 자신의 이동이고, 콜백 주소에는
 * 로그인 코드가 붙어 온다.
 *
 * 스크립트는 HTML에 적지 않고 여기서 붙인다. 운영 CSP가 인라인 스크립트를 막는다.
 */

// GA4 측정 ID(G-로 시작). 비어 있으면 아무것도 하지 않는다. 페이지 소스에 그대로 보이는 값이라 비밀이 아니다
export const GA_MEASUREMENT_ID = 'G-RMQ04MSDLN';

const PRODUCTION_HOST = 'jsjlog.me';
const EXCLUDED_ROUTE_NAMES = new Set(['admin-login', 'oauth-callback']);

export function shouldTrack(route) {
	return !route.meta?.requiresAdmin && !EXCLUDED_ROUTE_NAMES.has(route.name);
}

/** 해시만 바뀐 이동(같은 글 안의 목차·댓글 이동)은 새 페이지가 아니다 */
export function pageKey(route) {
	return route.fullPath.split('#')[0];
}

export function createPageTracker({
	measurementId = GA_MEASUREMENT_ID,
	productionHost = PRODUCTION_HOST,
	win = globalThis.window,
	doc = globalThis.document,
} = {}) {
	let started = false;
	let currentKey = null;
	// 제목이 API 응답 뒤에 정해지는 화면(글 상세)은 제목을 맞출 때까지 기록을 미룬다
	let pendingKey = null;

	function ready() {
		if (started) {
			return true;
		}
		if (!measurementId || win?.location?.hostname !== productionHost) {
			return false;
		}

		win.dataLayer = win.dataLayer || [];
		// gtag.js 는 arguments 객체를 그대로 받는다. 배열로 바꿔 넣으면 명령을 읽지 못한다
		win.gtag = function gtag() {
			win.dataLayer.push(arguments);
		};
		win.gtag('js', new Date());
		// 첫 화면 기록도 라우터가 제목을 맞춘 뒤 직접 보낸다. 광고용 신호는 쓰지 않는다
		win.gtag('config', measurementId, {
			send_page_view: false,
			allow_google_signals: false,
			allow_ad_personalization_signals: false,
		});

		const script = doc.createElement('script');
		script.async = true;
		script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(measurementId)}`;
		doc.head.appendChild(script);

		started = true;
		return true;
	}

	function send(key) {
		win.gtag('event', 'page_view', {
			page_location: `${win.location.origin}${key}`,
			page_title: doc.title,
		});
	}

	return {
		trackRoute(route, { waitForTitle = false } = {}) {
			const key = pageKey(route);
			if (key === currentKey) {
				return;
			}
			currentKey = key;
			// 제목을 기다리던 글을 떠났다. 보지 않은 화면은 남기지 않는다
			pendingKey = null;

			if (!shouldTrack(route) || !ready()) {
				return;
			}
			if (waitForTitle) {
				pendingKey = key;
				return;
			}
			send(key);
		},

		/** 제목을 맞춘 뒤 부른다. 같은 화면에서 여러 번 불러도 한 번만 보낸다 */
		trackPendingPage() {
			if (pendingKey === null) {
				return;
			}
			const key = pendingKey;
			pendingKey = null;
			send(key);
		},
	};
}

const tracker = createPageTracker();

export function trackRoute(route, options) {
	tracker.trackRoute(route, options);
}

export function trackPendingPage() {
	tracker.trackPendingPage();
}
