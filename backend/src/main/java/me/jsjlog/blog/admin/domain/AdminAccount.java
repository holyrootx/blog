package me.jsjlog.blog.admin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.jsjlog.blog.common.domain.BaseEntity;

/**
 * 관리자 계정.
 *
 * 비밀번호 원문은 어디에도 두지 않는다. BCrypt 해시만 저장한다.
 * 해시는 DB 가 만들어 주지 않는다 — 애플리케이션이 만든 값을 그대로 넣는다.
 */
@Getter
@Entity
@Table(name = "admin_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt 해시는 언제나 60자다. 강도나 알고리즘이 바뀔 여지를 두고 넉넉히 잡는다 */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AdminRole role;

    public AdminAccount(String username, String passwordHash, AdminRole role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /** 비밀번호 변경. 넘어오는 값은 이미 해시된 것이어야 한다 */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
