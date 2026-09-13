import { ref } from 'vue';

import { getAdminSidebarMenus } from '../api/adminApi';
import { getVisibleAdminMenus } from './adminMenus';

// 사이드바와 화면 제목 줄(어느 메뉴 아래인지 표시)이 같은 목록을 쓴다.
// 모듈 수준에 두어 한 번만 불러서 나눠 쓴다
const menus = ref([]);
let loadPromise = null;

export function useAdminSidebarMenus() {
  if (!loadPromise) {
    loadPromise = load();
  }

  return { menus };
}

/**
 * 메뉴를 고치면 사이드바도 달라진다.
 * 캐시를 버리고 다시 읽는다 (새로고침 없이 반영되도록).
 */
export function reloadAdminSidebarMenus() {
  loadPromise = load();
  return loadPromise;
}

function load() {
  return getAdminSidebarMenus()
    .then((loaded) => {
      menus.value = getVisibleAdminMenus(loaded);
    })
    .catch((error) => {
      menus.value = [];
      console.warn(error);
    });
}

/** 현재 경로가 속한 그룹의 이름. 못 찾으면 빈 문자열 */
export function findMenuGroupName(routePath) {
  const group = menus.value.find((menu) => menu.menuType === 'GROUP'
    && (menu.items ?? []).some((item) => item.routePath === routePath));

  return group?.menuName ?? '';
}
