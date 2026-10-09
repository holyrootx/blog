package me.jsjlog.blog.member.repository;

import java.util.List;

import me.jsjlog.blog.member.domain.MemberStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberStatusHistoryRepository extends JpaRepository<MemberStatusHistory, Long> {

    List<MemberStatusHistory> findAllByMember_IdOrderByIdAsc(Long memberId);
}
