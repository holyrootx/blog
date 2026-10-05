/**
 * 신고를 "신고 당시 본문" 이 같은 것끼리 묶는다. 대개는 하나다.
 *
 * 확인할 수 없는 신고는 본문이 null 이다. null 끼리 같은 본문으로 묶으면 보관 기간 만료와
 * 기록 없음이 한데 섞이므로, 상태와 신고 뒤 수정 여부까지 같아야 같은 묶음이다.
 */
export function groupReportsByReportedContent(reports = []) {
  const groups = [];

  for (const report of reports ?? []) {
    const status = report?.reportedContentStatus ?? 'NOT_RECORDED';
    const confirmed = status === 'CONFIRMED';
    const content = confirmed ? (report.reportedContent ?? '') : null;
    // 확인하지 못한 신고는 수정 여부도 모른다. false 로 두면 "안 고쳤다" 로 읽힌다
    const editedAfterReport = confirmed ? report.editedAfterReport === true : null;

    let group = groups.find((item) => item.status === status
      && item.content === content
      && item.editedAfterReport === editedAfterReport);

    if (!group) {
      group = { status, content, editedAfterReport, reports: [] };
      groups.push(group);
    }

    group.reports.push(report);
  }

  return groups;
}

/** 신고 당시 본문을 보여 줄 수 없을 때 그 까닭 */
export function reportedContentNotice(group, { contentPurged = false } = {}) {
  if (!group || group.status === 'CONFIRMED') {
    return '';
  }

  if (group.status === 'EXPIRED') {
    return contentPurged
      ? '보관 기간이 지나 원문을 파기해서 신고 당시 내용을 확인할 수 없습니다.'
      : '보관 기간 6개월이 지나 기록을 파기해서, 신고 당시 내용과 그 뒤 수정 여부를 확인할 수 없습니다.';
  }

  return '변경 기록이 남아 있지 않아 신고 당시 내용과 그 뒤 수정 여부를 확인할 수 없습니다. '
    + '기록 기능이 생기기 전에 쓴 댓글일 수 있습니다.';
}
