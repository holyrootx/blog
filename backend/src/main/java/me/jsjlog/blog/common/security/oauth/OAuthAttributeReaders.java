package me.jsjlog.blog.common.security.oauth;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.member.domain.AuthProvider;
import org.springframework.stereotype.Component;

/**
 * 등록 이름으로 알맞은 {@link OAuthAttributeReader} 를 골라 읽는다.
 *
 * <p>제공자를 추가하는 사람이 고칠 곳을 하나로 줄이는 장치다. 여기에 if 나 switch 를 두면
 * 새 제공자를 붙일 때 클래스 하나를 만들고 <b>이 파일도</b> 고쳐야 하고, 그 한쪽을 잊으면
 * 로그인 버튼은 있는데 콜백에서만 실패한다. 스프링이 모아 준 빈 목록으로 표를 만들면
 * 클래스를 추가하는 것만으로 끝난다.</p>
 */
@Component
public class OAuthAttributeReaders {

    private final Map<AuthProvider, OAuthAttributeReader> readers;

    public OAuthAttributeReaders(List<OAuthAttributeReader> readers) {
        this.readers = new EnumMap<>(AuthProvider.class);

        for (OAuthAttributeReader reader : readers) {
            OAuthAttributeReader previous = this.readers.put(reader.provider(), reader);

            // 같은 제공자를 둘이 맡으면 어느 쪽이 이겼는지가 빈 등록 순서에 달린다.
            // 기동 때 멈추는 편이 낫다 — 운영에서 엉뚱한 파서가 도는 것보다
            if (previous != null) {
                throw new IllegalStateException(
                        "%s 를 읽는 구현이 둘 있습니다: %s, %s".formatted(
                                reader.provider(),
                                previous.getClass().getSimpleName(),
                                reader.getClass().getSimpleName()
                        )
                );
            }
        }
    }

    /**
     * @param registrationId Spring Security 설정에 적은 등록 이름 (google, kakao, naver)
     * @param attributes 제공자가 준 원본 응답
     */
    public OAuthUserInfo read(String registrationId, Map<String, Object> attributes) {
        AuthProvider provider = AuthProvider.from(registrationId);
        OAuthAttributeReader reader = readers.get(provider);

        // 설정에는 등록했는데 읽는 구현을 안 만든 경우다. 열어 둔 로그인 버튼이
        // 콜백에서만 죽는 상황이라 원인을 알기 어려우니 제공자 이름을 남긴다
        if (reader == null) {
            throw new BlogException(
                    ErrorCode.OAUTH_PROVIDER_NOT_SUPPORTED,
                    "%s 응답을 읽는 구현이 없습니다.".formatted(provider)
            );
        }

        return reader.read(attributes);
    }
}
