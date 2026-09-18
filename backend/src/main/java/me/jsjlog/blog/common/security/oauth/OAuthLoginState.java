package me.jsjlog.blog.common.security.oauth;

/**
 * 소셜 인증을 마친 뒤 회원이 다음으로 진행할 단계.
 *
 * <p>활성 회원은 바로 {@code MemberPrincipal} 이 되므로 여기에 포함하지 않는다.
 * 이 상태는 아직 로그인된 회원이 아니라 가입 또는 재가입 절차가 남은 인증 결과에만 사용한다.</p>
 */
public enum OAuthLoginState {

    SIGNUP_REQUIRED,
    REACTIVATION_REQUIRED
}
