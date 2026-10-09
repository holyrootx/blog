package me.jsjlog.blog.member.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.jsjlog.blog.common.code.ActorTypeCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 회원 상태가 바뀐 기록. 추가만 하고 고치거나 지우지 않는다.
 *
 * <p>회원 번호와 코드만 담는다 — 이름·이메일 같은 개인정보는 두지 않는다. 그래서 회원 행이 있는 동안
 * 함께 보관한다. 메모에도 개인정보를 적지 않는다.</p>
 *
 * <p>상태가 그대로인 기록도 있다. 예: 복원 기간 만료는 탈퇴 → 탈퇴, 까닭만 RESTORE_EXPIRED 다.</p>
 *
 * <p>코드 칸은 varchar 다. 이유는 {@link MemberStatus} 와 같다.</p>
 */
@Getter
@Entity
@Table(
        name = "member_status_history",
        indexes = @Index(name = "idx_member_status_history_member", columnList = "member_id, created_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_member_status_history_member"))
    private Member member;

    /** 가입처럼 이전 상태가 없으면 비어 있다 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_member_status_code", length = 30)
    private MemberStatusCode fromMemberStatusCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_member_status_code", nullable = false, length = 30)
    private MemberStatusCode toMemberStatusCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reason_code", nullable = false, length = 30)
    private MemberStatusReasonCode reasonCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "actor_type_code", nullable = false, length = 20)
    private ActorTypeCode actorTypeCode;

    /** 처리한 회원. 본인이면 그 회원, 관리자면 관리자 번호, 시스템이면 비어 있다 */
    @Column(name = "actor_member_id")
    private Long actorMemberId;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public MemberStatusHistory(Member member, MemberStatusCode from, MemberStatusCode to,
                               MemberStatusReasonCode reason, ActorTypeCode actorType, Long actorMemberId,
                               LocalDateTime createdAt) {
        this.member = member;
        this.fromMemberStatusCode = from;
        this.toMemberStatusCode = to;
        this.reasonCode = reason;
        this.actorTypeCode = actorType;
        this.actorMemberId = actorMemberId;
        this.createdAt = createdAt;
    }
}
