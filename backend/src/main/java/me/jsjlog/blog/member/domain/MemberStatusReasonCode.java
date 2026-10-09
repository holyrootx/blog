package me.jsjlog.blog.member.domain;

import me.jsjlog.blog.common.code.CommonCodeType;

/** 회원 상태가 바뀐 까닭. 공통 코드 그룹 MEMBER_STATUS_REASON 과 짝이다 */
public enum MemberStatusReasonCode implements CommonCodeType {

    SIGNUP,
    WITHDRAW,

    /** 탈퇴 후 복원 기간 안에 "예전 계정 쓰기" 를 골랐다 */
    REACTIVATE,

    /** 탈퇴 후 복원 기간 안에 "새로 만들기" 를 골랐다. 예전 회원은 탈퇴 그대로, 복원 정보만 지운다 */
    REJOIN,

    SUSPEND,
    UNSUSPEND,

    /** 복원 기간이 지나 복원 정보를 지웠다. 상태는 탈퇴 그대로다 */
    RESTORE_EXPIRED;

    @Override
    public String groupCode() {
        return "MEMBER_STATUS_REASON";
    }
}
