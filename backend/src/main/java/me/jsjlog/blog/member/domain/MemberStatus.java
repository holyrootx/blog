package me.jsjlog.blog.member.domain;

import java.time.Duration;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.jsjlog.blog.common.domain.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 회원의 지금 상태. 회원 1명당 1행이고 키가 회원 번호다.
 *
 * <p>상태 칸에는 코드만 둔다({@link MemberStatusCode}). 화면 이름은 공통 코드 MEMBER_STATUS 에서 가져온다.
 * 코드 칸은 varchar 다 — MySQL enum 타입이면 공통 코드에 값을 더할 때마다 표를 고쳐야 한다.
 * 상태가 바뀐 기록은 {@link MemberStatusHistory} 에 쌓는다.</p>
 *
 * <p><b>탈퇴하면 제공자 ID 를 회원 행에서 여기로 옮긴다.</b> 복원 기간({@link #RESTORE_PERIOD}) 동안
 * 같은 소셜 계정으로 돌아오면 여기서 찾아 회원 행에 되돌린다. 기간이 지나면 지운다 — 그 뒤로 같은
 * 계정은 새 회원이 된다. 회원 행에 남겨 두지 않는 까닭은, 탈퇴한 사람을 식별하는 값이 회원 표에
 * 섞여 있지 않게 하려는 것이다.</p>
 *
 * <p>값을 바꾸는 메서드는 {@link Member} 만 부른다. 이력은 {@code MemberStatusService} 가 남긴다.</p>
 */
@Getter
@Entity
@Table(
        name = "member_status",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_status_restore",
                columnNames = {"restore_provider", "restore_provider_user_id"}
        ),
        indexes = {
                @Index(name = "idx_member_status_code", columnList = "member_status_code"),
                @Index(name = "idx_member_status_restore_expires", columnList = "restore_expires_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberStatus extends BaseEntity {

    /** 탈퇴 후 복원할 수 있는 기간. 개인정보 처리방침에 적은 서비스 기준이다 */
    public static final Duration RESTORE_PERIOD = Duration.ofDays(30);

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", foreignKey = @ForeignKey(name = "fk_member_status_member"))
    private Member member;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "member_status_code", nullable = false, length = 30)
    private MemberStatusCode memberStatusCode;

    @Column(name = "status_changed_at", nullable = false)
    private LocalDateTime statusChangedAt;

    /** 아래 셋은 탈퇴 후 복원 기간 동안만 채운다 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "restore_provider", length = 20)
    private AuthProvider restoreProvider;

    @Column(name = "restore_provider_user_id", length = 255)
    private String restoreProviderUserId;

    @Column(name = "restore_expires_at")
    private LocalDateTime restoreExpiresAt;

    MemberStatus(Member member, MemberStatusCode code, LocalDateTime now) {
        this.member = member;
        this.memberStatusCode = code;
        this.statusChangedAt = now;
    }

    void changeTo(MemberStatusCode code, LocalDateTime now) {
        this.memberStatusCode = code;
        this.statusChangedAt = now;
    }

    /** 탈퇴. 제공자 ID 가 없는 회원(아이디·비밀번호 회원)은 복원할 길이 없으므로 기한도 두지 않는다 */
    void withdraw(AuthProvider provider, String providerUserId, LocalDateTime now) {
        changeTo(MemberStatusCode.WITHDRAWN, now);
        if (providerUserId != null) {
            this.restoreProvider = provider;
            this.restoreProviderUserId = providerUserId;
            this.restoreExpiresAt = now.plus(RESTORE_PERIOD);
        }
    }

    public boolean isRestorable(LocalDateTime now) {
        return memberStatusCode == MemberStatusCode.WITHDRAWN
                && restoreProviderUserId != null
                && restoreExpiresAt != null
                && restoreExpiresAt.isAfter(now);
    }
}
