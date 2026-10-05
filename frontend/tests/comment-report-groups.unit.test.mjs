import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  groupReportsByReportedContent,
  reportedContentNotice,
} from '../src/features/admin/data/commentReportGroups.js';

const report = (id, fields) => ({ id, reasonLabel: '욕설·비방', ...fields });

test('같은 신고 당시 본문은 한 묶음이고 신고 뒤 수정 표시는 확인된 경우에만 붙는다', () => {
  const groups = groupReportsByReportedContent([
    report(1, { reportedContentStatus: 'CONFIRMED', reportedContent: '원문', editedAfterReport: true }),
    report(2, { reportedContentStatus: 'CONFIRMED', reportedContent: '원문', editedAfterReport: true }),
  ]);

  assert.equal(groups.length, 1);
  assert.equal(groups[0].content, '원문');
  assert.equal(groups[0].editedAfterReport, true);
  assert.deepEqual(groups[0].reports.map((item) => item.id), [1, 2]);
});

test('확인할 수 없는 신고는 본문이 null 이어도 깨지지 않고, 까닭이 다르면 따로 묶는다', () => {
  const groups = groupReportsByReportedContent([
    report(1, { reportedContentStatus: 'EXPIRED', reportedContent: null, editedAfterReport: null }),
    report(2, { reportedContentStatus: 'NOT_RECORDED', reportedContent: null, editedAfterReport: null }),
    report(3, { reportedContentStatus: 'EXPIRED', reportedContent: null, editedAfterReport: null }),
  ]);

  assert.deepEqual(groups.map((group) => group.status), ['EXPIRED', 'NOT_RECORDED']);
  assert.deepEqual(groups[0].reports.map((item) => item.id), [1, 3]);
  groups.forEach((group) => {
    assert.equal(group.content, null);
    assert.equal(group.editedAfterReport, null, '모르는 것을 "안 고쳤다" 로 바꾸지 않는다');
  });
});

test('확인한 본문과 확인 못 한 신고는 섞이지 않는다', () => {
  const groups = groupReportsByReportedContent([
    report(1, { reportedContentStatus: 'CONFIRMED', reportedContent: '원문', editedAfterReport: false }),
    report(2, { reportedContentStatus: 'EXPIRED', reportedContent: null, editedAfterReport: null }),
  ]);

  assert.equal(groups.length, 2);
  assert.equal(groups[0].editedAfterReport, false);
  assert.equal(reportedContentNotice(groups[0]), '');
  assert.match(reportedContentNotice(groups[1]), /6개월이 지나/);
});

test('상태가 빠진 옛 응답이나 빈 목록도 처리한다', () => {
  assert.deepEqual(groupReportsByReportedContent(), []);
  assert.deepEqual(groupReportsByReportedContent(null), []);

  const [group] = groupReportsByReportedContent([report(1, {})]);
  assert.equal(group.status, 'NOT_RECORDED');
  assert.match(reportedContentNotice(group), /기록이 남아 있지 않아/);
});

test('원문을 파기한 댓글은 파기했다고 알린다', () => {
  const [group] = groupReportsByReportedContent([report(1, { reportedContentStatus: 'EXPIRED' })]);

  assert.match(reportedContentNotice(group, { contentPurged: true }), /원문을 파기해서/);
});
