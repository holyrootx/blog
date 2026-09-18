package me.jsjlog.blog.member.domain;

/**
 * 회원 상태.
 *
 * <p>{@link #WITHDRAWN} 도 로그인 자체는 들어온다 — 같은 소셜 계정으로 돌아오면 예전 행을
 * 찾아 되살릴지 물어야 하기 때문이다. 그래서 "로그인 가능한가" 를 이 enum 이 답하지 않는다.
 * 상태별로 할 일이 다르고, 그 판단은 로그인 흐름이 한다.</p>
 */
public enum MemberStatus {

    ACTIVE,

    /** 스팸 등으로 막힌 상태. 로그인은 거부한다 */
    SUSPENDED,

    /** 탈퇴. 행은 남아 있다 — 댓글이 이 행의 닉네임을 보고 이름을 표시한다 */
    WITHDRAWN;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
