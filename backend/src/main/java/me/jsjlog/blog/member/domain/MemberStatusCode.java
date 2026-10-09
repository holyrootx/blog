package me.jsjlog.blog.member.domain;

import me.jsjlog.blog.common.code.CommonCodeType;

/**
 * 회원 상태 코드. 공통 코드 그룹 MEMBER_STATUS 와 짝이다 — 화면 이름은 공통 코드에서 가져온다.
 *
 * <p>{@link #WITHDRAWN} 도 로그인 자체는 들어온다 — 같은 소셜 계정으로 돌아오면 복원할지
 * 물어야 하기 때문이다. 그래서 "로그인 가능한가" 를 이 enum 이 답하지 않는다.
 * 상태별로 할 일이 다르고, 그 판단은 로그인 흐름이 한다.</p>
 */
public enum MemberStatusCode implements CommonCodeType {

    ACTIVE,

    /** 스팸 등으로 막힌 상태. 로그인은 거부한다 */
    SUSPENDED,

    /** 탈퇴. 행은 남아 있다 — 댓글이 이 행의 닉네임을 보고 이름을 표시한다 */
    WITHDRAWN;

    @Override
    public String groupCode() {
        return "MEMBER_STATUS";
    }

    public boolean isActive() {
        return this == ACTIVE;
    }
}
