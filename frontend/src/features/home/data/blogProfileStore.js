import { readonly, ref, shallowRef } from 'vue';

import { getBlogProfile } from '../api/homeApi';

/**
 * 블로그 주인 프로필. 헤더 이름, 대문 소개, 글쓴이 표시가 같은 값을 쓴다.
 *
 * 전에는 화면마다 따로 받아서, 글에서 다른 글로 옮길 때마다 같은 요청이 나가고 헤더 이름이
 * "JSJ.log" 로 돌아갔다가 다시 바뀌었다. 여기 한 벌만 두고, 새로 받는 동안에도 받아 둔 값을 계속 보여 준다.
 */
const profile = shallowRef(null);
const loading = ref(true);
let pending = null;

export const blogProfile = readonly(profile);
export const blogProfileLoading = readonly(loading);

/**
 * 새로 받는다. 이미 받는 중이면 그 요청을 같이 기다린다.
 *
 * 공개 화면 껍데기(PublicLayout)가 뜰 때 한 번 부른다. 관리자 화면에서 프로필을 고치고 돌아오면
 * 껍데기가 새로 뜨므로 그때 다시 받는다.
 *
 * 실패하면 받아 둔 값을 그대로 둔다. 프로필을 못 받아도 글은 읽을 수 있어야 한다.
 */
export function refreshBlogProfile() {
  if (!pending) {
    pending = getBlogProfile()
      .then((found) => {
        profile.value = found ?? null;
        return profile.value;
      })
      .catch((error) => {
        console.error(error);
        return profile.value;
      })
      .finally(() => {
        loading.value = false;
        pending = null;
      });
  }

  return pending;
}
