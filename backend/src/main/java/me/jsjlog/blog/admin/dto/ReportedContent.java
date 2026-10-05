package me.jsjlog.blog.admin.dto;

/**
 * 신고 당시 본문을 확인한 결과.
 *
 * 확인한 본문과, 확인할 수 없는 상태를 나눈다. 기록이 없다고 해서 "고치지 않았다" 고
 * 단정하거나 지금 본문으로 대신하면, 신고된 말 대신 고친 말을 보고 판단하게 된다.
 */
public record ReportedContent(String content, Status status, Boolean editedAfterReport) {

    public enum Status {
        /** 기록으로 확인했다 */
        CONFIRMED,
        /** 보관 기간이 지나 기록이나 원문을 파기해서 확인할 수 없다 */
        EXPIRED,
        /** 기록 기능이 생기기 전이라 남은 기록이 없어 확인할 수 없다 */
        NOT_RECORDED
    }

    public static ReportedContent confirmed(String content, boolean editedAfterReport) {
        return new ReportedContent(content, Status.CONFIRMED, editedAfterReport);
    }

    /** 본문도, 신고 뒤에 고쳤는지도 모른다 */
    public static ReportedContent unknown(Status reason) {
        return new ReportedContent(null, reason, null);
    }
}
