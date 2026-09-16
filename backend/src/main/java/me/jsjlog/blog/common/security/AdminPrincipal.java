package me.jsjlog.blog.common.security;

import me.jsjlog.blog.admin.domain.AdminAccount;
import me.jsjlog.blog.admin.domain.AdminRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 로그인한 관리자.
 *
 * 권한은 계정에 저장된 값을 그대로 쓴다. 여기에 ROLE_ADMIN 을 박아 두면
 * DB 에 권한 컬럼을 둔 의미가 없어지고, 나중에 권한을 나눌 때 두 곳을 고쳐야 한다.
 *
 * 비밀번호 해시를 들고 있으므로 이 객체를 그대로 응답에 담지 않는다.
 */
public record AdminPrincipal(String username, String passwordHash, AdminRole role) implements UserDetails {

    public static AdminPrincipal from(AdminAccount account) {
        return new AdminPrincipal(account.getUsername(), account.getPasswordHash(), account.getRole());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getAuthority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
