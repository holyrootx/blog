import { shallowRef } from 'vue';

// 댓글은 페이지 밖에 있을 수 있다. 라우터는 댓글 영역이 대상을 받은 뒤에 이동한다.
export const commentTargetNavigation = shallowRef(null);

export function cancelCommentTargetNavigation() {
  commentTargetNavigation.value?.finish('cancelled');
  commentTargetNavigation.value = null;
}

export function requestCommentTargetNavigation(postId, commentId) {
  cancelCommentTargetNavigation();
  return new Promise((resolve) => {
    commentTargetNavigation.value = {
      postId: String(postId),
      commentId: String(commentId),
      finish: resolve,
    };
  });
}
