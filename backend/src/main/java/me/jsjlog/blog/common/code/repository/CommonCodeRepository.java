package me.jsjlog.blog.common.code.repository;

import java.util.List;

import me.jsjlog.blog.common.code.domain.CommonCode;
import me.jsjlog.blog.common.code.domain.CommonCodeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommonCodeRepository extends JpaRepository<CommonCode, CommonCodeId> {

    List<CommonCode> findAllByOrderByGroupCodeAscSortOrderAscCodeAsc();

    List<CommonCode> findAllByGroupCodeOrderBySortOrderAscCodeAsc(String groupCode);

    @Query("select coalesce(max(c.sortOrder), 0) from CommonCode c where c.groupCode = :groupCode")
    int findMaxSortOrder(@Param("groupCode") String groupCode);
}
