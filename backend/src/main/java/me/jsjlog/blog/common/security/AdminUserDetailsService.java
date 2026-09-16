package me.jsjlog.blog.common.security;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.repository.AdminAccountRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * admin_account 테이블에서 관리자를 찾는다.
 *
 * 여기서 던지는 예외는 화면까지 그대로 가지 않는다. Spring Security 가
 * "아이디가 없다"와 "비밀번호가 틀리다"를 같은 응답으로 덮는다 —
 * 둘을 구분해 주면 어떤 아이디가 존재하는지 알려주는 꼴이 된다.
 *
 * 계정이 한 건도 없으면 아무도 로그인하지 못한다. 계정을 넣지 않은 채 배포했을 때
 * 열린 상태가 되는 것보다 막힌 상태가 되는 편이 낫다.
 */
@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminAccountRepository adminAccountRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminPrincipal loadUserByUsername(String username) {
        return adminAccountRepository.findByUsername(username)
                .map(AdminPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("관리자를 찾을 수 없습니다."));
    }
}
