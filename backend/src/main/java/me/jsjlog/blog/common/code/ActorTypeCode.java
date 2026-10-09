package me.jsjlog.blog.common.code;

/** 처리한 쪽. 공통 코드 그룹 ACTOR_TYPE 과 짝이다 */
public enum ActorTypeCode implements CommonCodeType {

    /** 회원 본인 */
    SELF,
    ADMIN,

    /** 정해진 일정으로 서버가 한 일. 예: 복원 기간 만료 정리 */
    SYSTEM;

    @Override
    public String groupCode() {
        return "ACTOR_TYPE";
    }
}
