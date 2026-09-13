package me.jsjlog.blog.admin.repository;

import me.jsjlog.blog.admin.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuRepository extends JpaRepository<Menu,Long>, MenuRepositoryCustom {

    /**
     * 그룹 삭제 가능 여부 판단용.
     * 항목이 하나라도 남아 있으면 그룹을 지울 수 없다
     * (parent_id 가 가리킬 곳을 잃는다).
     *
     * 메서드 이름으로 만드는 파생 쿼리 대신 직접 적는다.
     * Menu 에는 parentId 필드가 없고 getParentId() 게터만 있어서
     * existsByParentId 라고 쓰면 어느 경로로 해석될지 모호하다.
     */
    @Query("select count(menu) > 0 from Menu menu where menu.parent.id = :parentId")
    boolean existsByParentMenuId(@Param("parentId") Long parentId);

    /** 그룹들 중 마지막 순서. 하나도 없으면 0 */
    @Query("select coalesce(max(menu.sortOrder), 0) from Menu menu where menu.parent is null")
    Long findMaxGroupSortOrder();

    /** 한 그룹에 속한 항목들 중 마지막 순서. 하나도 없으면 0 */
    @Query("select coalesce(max(menu.sortOrder), 0) from Menu menu where menu.parent.id = :parentId")
    Long findMaxItemSortOrder(@Param("parentId") Long parentId);
}
