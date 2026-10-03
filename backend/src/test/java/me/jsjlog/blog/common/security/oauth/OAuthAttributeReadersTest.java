package me.jsjlog.blog.common.security.oauth;

import java.util.List;
import java.util.Map;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.member.domain.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 제공자별 응답 모양을 하나로 읽어 내는지 확인한다.
 *
 * 응답 예시는 각 제공자 문서의 형태를 그대로 옮겼다. 키 하나만 틀려도 로그인이
 * 콜백 단계에서만 깨지고, 그건 화면을 띄워 봐야 알 수 있어서 여기서 잡는다.
 */
class OAuthAttributeReadersTest {

    private final OAuthAttributeReaders readers = new OAuthAttributeReaders(List.of(
            new GoogleAttributeReader(),
            new KakaoAttributeReader(),
            new NaverAttributeReader()
    ));

    @Test
    @DisplayName("구글: 최상위 평평한 응답을 읽는다")
    void readsGoogle() {
        Map<String, Object> attributes = Map.of(
                "sub", "108712345678901234567",
                "name", "정성주",
                "email", "tjdwn1171@gmail.com",
                "picture", "https://lh3.googleusercontent.com/a/abc",
                "email_verified", true
        );

        OAuthUserInfo info = readers.read("google", attributes);

        assertThat(info.provider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(info.providerUserId()).isEqualTo("108712345678901234567");
        assertThat(info.nickname()).isEqualTo("정성주");
        assertThat(info.email()).isEqualTo("tjdwn1171@gmail.com");
        assertThat(info.profileImageUrl()).isEqualTo("https://lh3.googleusercontent.com/a/abc");
    }

    @Test
    @DisplayName("카카오: id 가 숫자로 와도 읽고, 중첩된 kakao_account 를 파고든다")
    void readsKakao() {
        Map<String, Object> attributes = Map.of(
                // JSON 숫자다. 형변환하면 여기서 터진다
                "id", 1234567890L,
                "kakao_account", Map.of(
                        "email", "someone@kakao.com",
                        "profile", Map.of(
                                "nickname", "코딩하는곰",
                                "profile_image_url", "https://k.kakaocdn.net/profile.jpg"
                        )
                )
        );

        OAuthUserInfo info = readers.read("kakao", attributes);

        assertThat(info.provider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(info.providerUserId()).isEqualTo("1234567890");
        assertThat(info.nickname()).isEqualTo("코딩하는곰");
        assertThat(info.email()).isEqualTo("someone@kakao.com");
        assertThat(info.profileImageUrl()).isEqualTo("https://k.kakaocdn.net/profile.jpg");
    }

    @Test
    @DisplayName("네이버: 모든 값이 response 아래 한 겹 들어 있다")
    void readsNaver() {
        Map<String, Object> attributes = Map.of(
                "resultcode", "00",
                "message", "success",
                "response", Map.of(
                        "id", "32742776",
                        "nickname", "네이버사용자",
                        "email", "someone@naver.com",
                        // 구글·카카오와 달리 _url 이 없다
                        "profile_image", "https://ssl.pstatic.net/profile.jpg"
                )
        );

        OAuthUserInfo info = readers.read("naver", attributes);

        assertThat(info.provider()).isEqualTo(AuthProvider.NAVER);
        assertThat(info.providerUserId()).isEqualTo("32742776");
        assertThat(info.nickname()).isEqualTo("네이버사용자");
        assertThat(info.email()).isEqualTo("someone@naver.com");
        assertThat(info.profileImageUrl()).isEqualTo("https://ssl.pstatic.net/profile.jpg");
    }

    @Test
    @DisplayName("카카오·네이버는 닉네임과 이메일이 선택 동의라 없이 올 수 있다")
    void readsKakaoWithoutOptionalConsent() {
        Map<String, Object> attributes = Map.of(
                "id", 1234567890L,
                "kakao_account", Map.of(
                        "profile_nickname_needs_agreement", true,
                        "email_needs_agreement", true
                )
        );

        OAuthUserInfo info = readers.read("kakao", attributes);

        assertThat(info.providerUserId()).isEqualTo("1234567890");
        assertThat(info.hasNickname()).isFalse();
        assertThat(info.hasEmail()).isFalse();
        assertThat(info.nickname()).isNull();
        assertThat(info.email()).isNull();
    }

    @Test
    @DisplayName("빈 문자열은 없는 것과 같게 다룬다")
    void treatsBlankAsMissing() {
        Map<String, Object> attributes = Map.of(
                "sub", "108712345678901234567",
                "name", "   ",
                "email", ""
        );

        OAuthUserInfo info = readers.read("google", attributes);

        assertThat(info.hasNickname()).isFalse();
        assertThat(info.hasEmail()).isFalse();
    }

    @Test
    @DisplayName("식별자가 없으면 회원을 만들 수 없으므로 막는다")
    void rejectsMissingProviderUserId() {
        Map<String, Object> attributes = Map.of("name", "이름만 있음");

        assertThatThrownBy(() -> readers.read("google", attributes))
                .isInstanceOf(BlogException.class)
                .hasMessageContaining("GOOGLE");
    }

    @Test
    @DisplayName("등록하지 않은 제공자 이름은 거부한다")
    void rejectsUnknownProvider() {
        assertThatThrownBy(() -> readers.read("apple", Map.of()))
                .isInstanceOf(BlogException.class)
                .hasMessageContaining("apple");
    }

    @Test
    @DisplayName("local 은 소셜 등록 이름으로 해석되지 않는다")
    void rejectsLocalAsRegistrationId() {
        assertThatThrownBy(() -> AuthProvider.from("local"))
                .isInstanceOf(BlogException.class);
    }

    @Test
    @DisplayName("같은 제공자를 맡는 구현이 둘이면 기동 단계에서 멈춘다")
    void rejectsDuplicateReaders() {
        assertThatThrownBy(() -> new OAuthAttributeReaders(List.of(
                new GoogleAttributeReader(),
                new GoogleAttributeReader()
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE");
    }
}
