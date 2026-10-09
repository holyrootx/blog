package me.jsjlog.blog.common.security.oauth;

import java.time.LocalDateTime;

import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberStatusCode;
import me.jsjlog.blog.member.domain.NicknameGenerator;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.member.repository.MemberStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 제공자에서 사용자를 받아 블로그 회원 또는 가입 대기 사용자로 변환한다.
 *
 * <p>Google·Kakao·Naver를 모두 일반 OAuth2 흐름으로 처리한다. 제공자별 응답 차이는
 * {@link OAuthAttributeReaders} 에서 끝내고, 이 서비스는 회원 조회와 상태 분기만 담당한다.</p>
 */
@Service
@Transactional
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String ACCOUNT_SUSPENDED = "account_suspended";

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final OAuthAttributeReaders attributeReaders;
    private final MemberRepository memberRepository;
    private final MemberStatusRepository memberStatusRepository;
    private final NicknameGenerator nicknameGenerator;
    private final AuditorAware<String> auditorProvider;

    @Autowired
    public CustomOAuth2UserService(
            OAuthAttributeReaders attributeReaders,
            MemberRepository memberRepository,
            MemberStatusRepository memberStatusRepository,
            NicknameGenerator nicknameGenerator,
            AuditorAware<String> auditorProvider
    ) {
        this(new DefaultOAuth2UserService(), attributeReaders, memberRepository, memberStatusRepository,
                nicknameGenerator, auditorProvider);
    }

    CustomOAuth2UserService(
            OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate,
            OAuthAttributeReaders attributeReaders,
            MemberRepository memberRepository,
            MemberStatusRepository memberStatusRepository,
            NicknameGenerator nicknameGenerator,
            AuditorAware<String> auditorProvider
    ) {
        this.delegate = delegate;
        this.attributeReaders = attributeReaders;
        this.memberRepository = memberRepository;
        this.memberStatusRepository = memberStatusRepository;
        this.nicknameGenerator = nicknameGenerator;
        this.auditorProvider = auditorProvider;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User providerUser = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthUserInfo userInfo = attributeReaders.read(registrationId, providerUser.getAttributes());

        long authenticationStartedAtNanos = System.nanoTime();
        return memberRepository
                .findByProviderAndProviderUserId(userInfo.provider(), userInfo.providerUserId())
                .map(member -> resolveExistingMember(member, userInfo, authenticationStartedAtNanos))
                .orElseGet(() -> resolveNewOrRestorable(userInfo));
    }

    /**
     * 회원 표에 없는 계정. 복원 기간 안의 탈퇴 회원이면 복원할지 묻고, 아니면 새로 가입한다.
     *
     * 기간은 여기서 직접 본다. 하루 한 번 도는 정리 작업이 아직 지우지 않았어도 기간이 지났으면 새 가입이다.
     */
    private OAuth2User resolveNewOrRestorable(OAuthUserInfo userInfo) {
        return memberStatusRepository
                .findRestorable(userInfo.provider(), userInfo.providerUserId(), LocalDateTime.now())
                .<OAuth2User>map(status -> PendingOAuthPrincipal.reactivation(userInfo, status.getMember().getNickname()))
                .orElseGet(() -> PendingOAuthPrincipal.signup(userInfo, suggestedNickname(userInfo)));
    }

    private OAuth2User resolveExistingMember(Member member, OAuthUserInfo userInfo, long authenticationStartedAtNanos) {
        if (member.getStatusCode() == MemberStatusCode.SUSPENDED) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(ACCOUNT_SUSPENDED),
                    "정지된 회원은 로그인할 수 없습니다."
            );
        }

        // 탈퇴하면 제공자 ID 를 회원 표에서 빼므로 여기 오지 않는다. 오면 옮기는 SQL 이 빠진 예전 데이터다
        if (member.getStatusCode() == MemberStatusCode.WITHDRAWN) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_changed"),
                    "계정 정보가 변경되었습니다. 다시 로그인해 주세요.");
        }

        if (memberRepository.updateActiveProfile(member.getId(), userInfo.email(), userInfo.profileImageUrl(),
                LocalDateTime.now(), auditorProvider.getCurrentAuditor().orElse("system")) != 1) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_changed"),
                    "계정 정보가 변경되었습니다. 다시 로그인해 주세요.");
        }
        // 부분 갱신이 영속성 컨텍스트를 비웠으므로 응답용 객체만 최신 프로필로 맞춘다.
        member.syncProfile(userInfo.email(), userInfo.profileImageUrl());
        return MemberPrincipal.ofSocial(member, authenticationStartedAtNanos);
    }

    private String suggestedNickname(OAuthUserInfo userInfo) {
        return userInfo.hasNickname() ? userInfo.nickname() : nicknameGenerator.generate();
    }
}
