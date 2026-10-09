package me.jsjlog.blog.member.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 회원 상태 조회와 조건부 갱신.
 *
 * <p>복원·새로 만들기·만료 정리가 같은 탈퇴 회원을 동시에 건드릴 수 있다. 그래서 바꾸는 쿼리는 모두
 * "지금도 그 상태일 때만" 조건을 함께 걸고, 바뀐 행이 1개일 때만 다음으로 간다. 먼저 끝난 쪽이 이기고
 * 늦은 쪽은 0행을 받는다.</p>
 */
public interface MemberStatusRepository extends JpaRepository<MemberStatus, Long> {

    /** 복원 기간 안의 탈퇴 회원. 같은 소셜 계정으로 돌아왔을 때 찾는다 */
    @Query("""
            select s from MemberStatus s join fetch s.member
            where s.restoreProvider = :provider and s.restoreProviderUserId = :providerUserId
                and s.restoreExpiresAt > :now
                and s.memberStatusCode = me.jsjlog.blog.member.domain.MemberStatusCode.WITHDRAWN
            """)
    Optional<MemberStatus> findRestorable(@Param("provider") AuthProvider provider,
                                          @Param("providerUserId") String providerUserId,
                                          @Param("now") LocalDateTime now);

    /** 기간이 지났는데 아직 정리되지 않은 복원 정보 */
    @Query("""
            select s.memberId from MemberStatus s
            where s.restoreProviderUserId is not null and s.restoreExpiresAt <= :now
            order by s.memberId
            """)
    List<Long> findExpiredRestoreIds(@Param("now") LocalDateTime now);

    /** 같은 소셜 계정의 기간 지난 복원 정보. 그 계정이 새로 가입할 때 먼저 정리한다 */
    @Query("""
            select s.memberId from MemberStatus s
            where s.restoreProvider = :provider and s.restoreProviderUserId = :providerUserId
                and s.restoreExpiresAt <= :now
            """)
    List<Long> findExpiredRestoreIds(@Param("provider") AuthProvider provider,
                                     @Param("providerUserId") String providerUserId,
                                     @Param("now") LocalDateTime now);

    /** 복원. 기간 안이고 아직 그 계정의 복원 정보가 남아 있을 때만 활동으로 되돌린다 */
    @Modifying(clearAutomatically = true)
    @Query("""
            update MemberStatus s
            set s.memberStatusCode = me.jsjlog.blog.member.domain.MemberStatusCode.ACTIVE,
                s.statusChangedAt = :now,
                s.restoreProvider = null, s.restoreProviderUserId = null, s.restoreExpiresAt = null,
                s.updatedAt = :now, s.updatedBy = :updatedBy
            where s.memberId = :memberId
                and s.memberStatusCode = me.jsjlog.blog.member.domain.MemberStatusCode.WITHDRAWN
                and s.restoreProvider = :provider and s.restoreProviderUserId = :providerUserId
                and s.restoreExpiresAt > :now
            """)
    int reactivate(@Param("memberId") Long memberId, @Param("provider") AuthProvider provider,
                   @Param("providerUserId") String providerUserId, @Param("now") LocalDateTime now,
                   @Param("updatedBy") String updatedBy);

    /** 새로 만들기를 골랐다. 예전 회원은 탈퇴 그대로 두고 복원 정보만 지운다 */
    @Modifying(clearAutomatically = true)
    @Query("""
            update MemberStatus s
            set s.restoreProvider = null, s.restoreProviderUserId = null, s.restoreExpiresAt = null,
                s.updatedAt = :now, s.updatedBy = :updatedBy
            where s.memberId = :memberId
                and s.memberStatusCode = me.jsjlog.blog.member.domain.MemberStatusCode.WITHDRAWN
                and s.restoreProvider = :provider and s.restoreProviderUserId = :providerUserId
                and s.restoreExpiresAt > :now
            """)
    int releaseForRejoin(@Param("memberId") Long memberId, @Param("provider") AuthProvider provider,
                         @Param("providerUserId") String providerUserId, @Param("now") LocalDateTime now,
                         @Param("updatedBy") String updatedBy);

    /** 기간이 지난 복원 정보를 지운다 */
    @Modifying(clearAutomatically = true)
    @Query("""
            update MemberStatus s
            set s.restoreProvider = null, s.restoreProviderUserId = null, s.restoreExpiresAt = null,
                s.updatedAt = :now, s.updatedBy = :updatedBy
            where s.memberId = :memberId
                and s.restoreProviderUserId is not null and s.restoreExpiresAt <= :now
            """)
    int expireRestore(@Param("memberId") Long memberId, @Param("now") LocalDateTime now,
                      @Param("updatedBy") String updatedBy);
}
