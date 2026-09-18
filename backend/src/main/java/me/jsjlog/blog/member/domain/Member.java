package me.jsjlog.blog.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.jsjlog.blog.common.domain.BaseEntity;

/**
 * 블로그 회원. 관리자도 회원이다 ({@code role = ADMIN}).
 *
 * <p><b>정체성은 {@code (provider, providerUserId)} 한 쌍이다.</b> 이메일로 회원을 찾지 않는다 —
 * 제공자가 이메일 소유를 검증했는지 보장할 수 없고, 카카오·네이버는 아예 안 줄 수도 있다.
 * 이메일로 찾기 시작하면 남의 이메일로 다른 제공자에 가입해 기존 계정을 가져갈 수 있다.
 * {@code email} 은 받아서 저장만 하는 값이다.</p>
 *
 * <p>{@code nickname} 에는 unique 를 걸지 않는다. 식별은 {@code providerUserId} 가 하고,
 * 닉네임은 화면에 보이는 이름일 뿐이다. 사칭은 작성자 뱃지로 구분한다. unique 를 걸면
 * 소셜 로그인 중간에 "사용 중인 닉네임입니다" 화면이 끼고, 탈퇴자가 이름을 영구 점유한다.</p>
 *
 * <p>널 가능한 컬럼에 unique 를 건 것이 둘 있다 ({@code username}, {@code (provider, providerUserId)}).
 * MySQL 의 unique 인덱스는 NULL 을 여러 개 허용하므로, {@code username} 이 없는 소셜 회원이
 * 아무리 많아도, {@code providerUserId} 가 없는 LOCAL 회원이 여럿이어도 충돌하지 않는다.
 * 이 성질에 기대고 있으니 DB 를 바꿀 때 확인해야 한다.</p>
 */
@Getter
@Entity
@Table(
        name = "member",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_provider", columnNames = {"provider", "provider_user_id"}),
                @UniqueConstraint(name = "uk_member_username", columnNames = {"username"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 요청에서 받지 않는다. 아래 정적 팩터리가 상수로 넣는다.
     *
     * DB 기본값도 USER 로 둔다. 손으로 INSERT 할 때 이 컬럼을 빼먹어도 관리자가 되지 않게 하려는 것이다.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "role",
            nullable = false,
            length = 20,
            columnDefinition = "varchar(20) not null default 'USER'"
    )
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AuthProvider provider;

    /** 제공자가 준 고유 번호. LOCAL 회원은 없다. 탈퇴해도 남긴다 — 재가입 때 예전 행을 찾는 열쇠다 */
    @Column(name = "provider_user_id", length = 255)
    private String providerUserId;

    /** 아이디·비밀번호 로그인에 쓰는 진짜 식별자. 소셜 회원은 없다 */
    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /** 화면에 보이는 이름. 탈퇴해도 남긴다 — 지우면 댓글창이 "탈퇴한 사용자" 로만 채워져 대화를 읽을 수 없다 */
    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    /** 회원을 찾는 데 쓰지 않는다. unique 도 걸지 않는다 */
    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    /**
     * 소셜 회원 가입.
     *
     * role 을 인자로 받지 않는다. 받게 만들면 그 인자가 어디선가 요청 값과 이어질 수 있다.
     */
    public static Member ofSocial(
            AuthProvider provider,
            String providerUserId,
            String nickname,
            String email,
            String profileImageUrl
    ) {
        Member member = new Member();

        member.role = MemberRole.USER;
        member.provider = provider;
        member.providerUserId = providerUserId;
        member.nickname = nickname;
        member.email = email;
        member.profileImageUrl = profileImageUrl;
        member.status = MemberStatus.ACTIVE;

        return member;
    }

    /**
     * 아이디·비밀번호로 로그인하는 관리자.
     *
     * <p>있는 회원을 관리자로 올리는 메서드는 만들지 않는다. 이건 처음부터 관리자인 행을
     * 만드는 것이고, 요청을 받는 어떤 경로에도 연결하지 않는다 — 계정 준비와 테스트용이다.</p>
     */
    public static Member ofLocalAdmin(String username, String passwordHash, String nickname) {
        Member member = new Member();

        member.role = MemberRole.ADMIN;
        member.provider = AuthProvider.LOCAL;
        member.username = username;
        member.passwordHash = passwordHash;
        member.nickname = nickname;
        member.status = MemberStatus.ACTIVE;

        return member;
    }

    /**
     * 탈퇴. 행을 지우지 않는다 — 댓글이 이 행을 가리키고 있어서 지우면 댓글이 사라지고
     * 답글이 부모를 잃는다.
     *
     * <p>지우는 값들은 다시 로그인하면 제공자가 채워 주는 것들이라 파기해도 복구에 지장이 없다.
     * {@code providerUserId} 와 {@code nickname} 은 남긴다.</p>
     */
    public void withdraw() {
        this.email = null;
        this.passwordHash = null;
        this.username = null;
        this.status = MemberStatus.WITHDRAWN;
    }

    /** 재가입에서 "예전 계정 쓰기" 를 고른 경우 */
    public void reactivate(String email, String profileImageUrl) {
        this.status = MemberStatus.ACTIVE;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * 재가입에서 "새로 만들기" 를 고른 경우. 예전 행과 소셜 계정의 연결을 끊는다.
     *
     * 끊고 나면 그 행은 아무도 되살릴 수 없다. 댓글이 없으면 지워도 된다 — 가리키는 게 없다.
     */
    public void detachProvider() {
        this.providerUserId = null;
    }

    /** 로그인할 때마다 제공자가 준 최신 값으로 맞춘다. 닉네임은 본인이 정한 값이라 건드리지 않는다 */
    public void syncProfile(String email, String profileImageUrl) {
        this.email = email;
        this.profileImageUrl = profileImageUrl;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /** 비밀번호 변경. 넘어오는 값은 이미 해시된 것이어야 한다 */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void suspend() {
        this.status = MemberStatus.SUSPENDED;
    }

    public boolean isAdmin() {
        return role.isAdmin();
    }
}
