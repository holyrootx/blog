import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mergeCommentItems } from '../src/features/post/data/commentItems.js';

const reply = id => ({ id, content: `답글 ${id}` });
const item = (id, replies = [], fields = {}) => ({ id, replies: replies.map(reply), replyHasNext: true, replyNextCursor: 10, ...fields });

test('context and normal pages merge without duplicate comments or replies or rewinding the reply cursor', () => {
  const loaded = [item(20), item(1, [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], { replyNextCursor: 12 })];
  const context = mergeCommentItems(loaded, [item(1, [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 25])]);
  assert.deepEqual(context.map(comment => comment.id), [20, 1]);
  assert.deepEqual(context[1].replies.map(comment => comment.id), [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 25]);
  assert.equal(context[1].replyNextCursor, 12);
  const nextPage = mergeCommentItems(context, [item(10), item(1, [1, 2, 3])]);
  assert.deepEqual(nextPage.map(comment => comment.id), [20, 10, 1]);
  assert.equal(nextPage[2].replies.length, 13);
  assert.equal(nextPage[2].replyNextCursor, 12);
});

test('new replies discovered by a notification can continue after a previously exhausted reply page', () => {
  const previous = item(1, [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], { replyNextCursor: null, replyHasNext: false });
  const [merged] = mergeCommentItems([previous], [item(1, [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 25])]);
  assert.equal(merged, previous);
  assert.equal(merged.replyHasNext, true);
  assert.equal(merged.replyNextCursor, 12);
});
