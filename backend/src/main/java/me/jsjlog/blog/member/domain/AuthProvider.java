package me.jsjlog.blog.member.domain;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;

/**
 * 회원이 어떤 방법으로 로그인하는지.
 *
 * DB 에 이름 그대로 저장한다. 회원의 정체성은 {@code (provider, providerUserId)} 한 쌍이고,
 * 이메일이나 닉네임은 정체성이 아니다 — 제공자가 안 줄 수도 있고 바뀔 수도 있다.
 */
public enum AuthProvider {

    /** 아이디·비밀번호 로그인. 관리자가 쓴다 */
    LOCAL,

    GOOGLE,
    KAKAO,
    NAVER;

    /**
     * Spring Security 의 registrationId(설정에 적은 등록 이름)를 제공자로 바꾼다.
     *
     * {@link #LOCAL} 로는 절대 해석하지 않는다. 소셜 콜백에서 들어온 이름이 LOCAL 로 읽히면
     * 비밀번호 없는 회원이 아이디·비밀번호 계정 자리에 앉는다.
     */
    public static AuthProvider from(String registrationId) {
        for (AuthProvider provider : values()) {
            if (provider.isSocial() && provider.name().equalsIgnoreCase(registrationId)) {
                return provider;
            }
        }

        throw new BlogException(
                ErrorCode.OAUTH_PROVIDER_NOT_SUPPORTED,
                "지원하지 않는 로그인 제공자입니다: " + registrationId
        );
    }

    public boolean isSocial() {
        return this != LOCAL;
    }
}
