package me.jsjlog.blog.history.repository;

import java.time.LocalDateTime;
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
}
