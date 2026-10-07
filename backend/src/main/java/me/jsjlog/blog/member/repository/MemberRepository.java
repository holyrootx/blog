package me.jsjlog.blog.member.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 회원 조회.
 *
 * <p><b>{@code findByEmail} 을 만들지 않는다.</b> 만드는 순간 이메일이 사실상 식별자가 되고,
 * 그러면 남의 이메일로 다른 제공자에 가입해 기존 계정을 접수할 수 있다. 제공자가 이메일
 * 소유를 검증했는지 우리는 알 수 없고, 카카오·네이버는 이메일을 안 줄 수도 있다.
 * 회원을 찾는 열쇠는 {@code (provider, providerUserId)} 뿐이다.</p>
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 소셜 로그인의 유일한 조회 경로. 탈퇴한 행도 같이 찾힌다 — 재가입을 판단해야 한다 */
    Optional<Member> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    /**
     * 관리자 로그인.
     *
     * role 을 조건에 넣는다. {@code findByUsername} 으로 찾아 놓고 권한을 나중에 보면,
     * 그 확인을 빠뜨린 경로가 하나 생기는 순간 일반 회원이 관리자 자리에 앉는다.
     */
    Optional<Member> findByUsernameAndRole(String username, MemberRole role);

    /** 상태 검사를 UPDATE와 함께 한다. 조회 이후 정지·탈퇴한 계정에는 프로필을 다시 쓰지 않는다. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Member m set m.email = :email, m.profileImageUrl = :profileImageUrl,
                m.updatedAt = :updatedAt, m.updatedBy = :updatedBy
            where m.id = :id and m.status = me.jsjlog.blog.member.domain.MemberStatus.ACTIVE
            """)
    int updateActiveProfile(@Param("id") Long id, @Param("email") String email,
                            @Param("profileImageUrl") String profileImageUrl,
                            @Param("updatedAt") LocalDateTime updatedAt, @Param("updatedBy") String updatedBy);

    /** 개인정보와 상태를 포함한 오래된 엔티티 전체를 덮어쓰지 않는다. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Member m set m.nickname = :nickname, m.updatedAt = :updatedAt, m.updatedBy = :updatedBy
            where m.id = :id and m.status = me.jsjlog.blog.member.domain.MemberStatus.ACTIVE
            """)
    int updateActiveNickname(@Param("id") Long id, @Param("nickname") String nickname,
                             @Param("updatedAt") LocalDateTime updatedAt, @Param("updatedBy") String updatedBy);

    /** 복구와 새 가입이 겹쳐도 먼저 완료된 선택을 오래된 조회 결과로 덮어쓰지 않는다. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Member m set m.status = me.jsjlog.blog.member.domain.MemberStatus.ACTIVE,
                m.email = :email, m.profileImageUrl = :profileImageUrl,
                m.updatedAt = :updatedAt, m.updatedBy = :updatedBy
            where m.id = :id and m.provider = :provider and m.providerUserId = :providerUserId
                and m.status = me.jsjlog.blog.member.domain.MemberStatus.WITHDRAWN
            """)
    int reactivateWithdrawn(@Param("id") Long id, @Param("provider") AuthProvider provider,
                            @Param("providerUserId") String providerUserId, @Param("email") String email,
                            @Param("profileImageUrl") String profileImageUrl,
                            @Param("updatedAt") LocalDateTime updatedAt, @Param("updatedBy") String updatedBy);

    /** 제공자 연결 해제와 새 회원 INSERT는 같은 트랜잭션에서 함께 성공하거나 함께 롤백한다. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Member m set m.providerUserId = null, m.updatedAt = :updatedAt, m.updatedBy = :updatedBy
            where m.id = :id and m.provider = :provider and m.providerUserId = :providerUserId
                and m.status = me.jsjlog.blog.member.domain.MemberStatus.WITHDRAWN
            """)
    int detachWithdrawnProvider(@Param("id") Long id, @Param("provider") AuthProvider provider,
                                @Param("providerUserId") String providerUserId,
                                @Param("updatedAt") LocalDateTime updatedAt, @Param("updatedBy") String updatedBy);

    /**
     * 그 권한을 가진 회원 전부.
     *
     * <p>운영자에게 알릴 일이 생겼을 때 받는 사람을 찾는 자리다. 지금은 관리자가 한 명이라
     * {@code Optional} 로도 되지만 복수로 둔다 — 한 명이라고 가정해 두면 둘이 되는 날
     * 알림이 한 명에게만 가고, 그건 안 가는 것보다 알아채기 어렵다.</p>
     */
    List<Member> findAllByRole(MemberRole role);
}
