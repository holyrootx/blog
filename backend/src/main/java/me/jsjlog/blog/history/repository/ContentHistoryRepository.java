package me.jsjlog.blog.history.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import me.jsjlog.blog.history.domain.ContentHistory;
import me.jsjlog.blog.history.domain.ContentHistory.Target;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentHistoryRepository extends JpaRepository<ContentHistory, Long> {

    List<ContentHistory> findByTargetTypeAndTargetIdOrderByIdDesc(Target targetType, Long targetId);

    @Modifying
    @Query("delete from ContentHistory h where h.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("delete from ContentHistory h where h.targetType = :targetType and h.targetId in :targetIds")
    int deleteByTargets(@Param("targetType") Target targetType, @Param("targetIds") Collection<Long> targetIds);

    /** 그 시각에 이미 기록이 쌓이고 있었는가. 이 기능이 생기기 전인지 가르는 데 쓴다 */
    boolean existsByCreatedAtLessThanEqual(LocalDateTime time);
}
