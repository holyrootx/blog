import { test } from 'node:test';
import assert from 'node:assert/strict';

import { canRestore, commentState } from '../src/features/admin/data/adminCommentRules.js';

const comment = (fields) => ({
  parentId: null,
  memberRole: 'USER',
  hidden: false,
  deletedByWriter: false,
  contentPurged: false,
  postPurged: false,
  answered: false,
  ...fields,
});

test('관리자는 자기가 가린 댓글만 되돌린다', () => {
  assert.equal(canRestore(comment({ hidden: true })), true);
});

test('글쓴이가 지운 댓글은 가렸든 아니든 되돌리지 않는다', () => {
  assert.equal(canRestore(comment({ hidden: true, deletedByWriter: true })), false);
  assert.equal(commentState(comment({ hidden: true, deletedByWriter: true })), '작성자 삭제');
});

test('원문을 파기했거나 원글이 영구 삭제된 댓글도 되돌리지 않는다', () => {
  assert.equal(canRestore(comment({ hidden: true, contentPurged: true })), false);
  assert.equal(canRestore(comment({ hidden: true, postPurged: true })), false);
});

test('보이는 댓글은 되돌릴 것이 없다', () => {
  assert.equal(canRestore(comment({})), false);
  assert.equal(commentState(comment({ hidden: true })), '숨김');
});
