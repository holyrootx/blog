package me.jsjlog.blog.admin.repository;

import me.jsjlog.blog.admin.domain.AdminAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {

    Optional<AdminAccount> findByUsername(String username);
}
