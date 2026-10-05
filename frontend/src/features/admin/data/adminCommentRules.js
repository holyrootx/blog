/** 관리자가 이 댓글에 답글을 달 수 있는가. 목록 단추와 알림에서 바로 여는 답글 창이 같은 기준을 쓴다 */
export function canReply(comment) {
  return comment.parentId === null
    && comment.memberRole !== 'ADMIN'
    && !comment.postPurged
    && !comment.hidden;
}

/** 목록 줄 앞에 붙는 상태 한 마디 */
export function commentState(comment) {
  if (comment.contentPurged) return '원문 파기';
  if (comment.postPurged) return '보관 중';
  if (comment.hidden) return '숨김';
  if (comment.parentId !== null) return '답글';
  if (comment.memberRole === 'ADMIN') return '관리자 댓글';
  return comment.answered ? '답변 완료' : '미답변';
}
