// 알림에서 따로 받은 스레드와 목록 페이지가 겹쳐도 한 번만 그린다.
export function mergeCommentItems(current, incoming) {
  const items = new Map(current.map((item) => [item.id, item]));
  incoming.forEach((item) => {
    const previous = items.get(item.id);
    if (!previous) {
      items.set(item.id, item);
      return;
    }
    const replies = new Map(previous.replies.map((reply) => [reply.id, reply]));
    item.replies.forEach((reply) => replies.set(reply.id, reply));
    const nextCursor = previous.replyNextCursor ?? (previous.replies.length
      ? Math.max(...previous.replies.map((reply) => reply.id)) : item.replyNextCursor);
    // 진행 중인 답글 조회·수정도 같은 객체를 잡고 있으므로 행의 참조를 유지한다.
    Object.assign(previous, {
      ...item,
      replies: [...replies.values()].sort((a, b) => a.id - b.id),
      // 더보기로 이미 진행한 커서를 초기 답글 페이지로 되돌리지 않는다.
      replyNextCursor: nextCursor,
      replyHasNext: previous.replyHasNext || item.replyHasNext,
    });
  });
  return [...items.values()].sort((a, b) => b.id - a.id);
}
