package me.jsjlog.blog.admin.domain;

import jakarta.persistence.*;
import lombok.*;
import me.jsjlog.blog.common.domain.BaseEntity;

@Getter
@Entity
@Table(name = "admin_menu")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Menu parent;

    @Column(name = "menu_name", nullable = false, length = 50)
    private String menuName;

    @Column(name = "menu_description", length = 200)
    private String menuDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "menu_type", nullable = false, length = 5)
    private MenuType menuType;

    @Column(name = "route_path", length = 200)
    private String routePath;

    @Column(name = "sort_order", nullable = false)
    private Long sortOrder;

    @Column(name = "is_visible", nullable = false)
    private Boolean visible;

    public Menu(
            String menuName,
            String menuDescription,
            MenuType menuType,
            String routePath,
            Long sortOrder,
            Boolean visible
    ) {
        this(null, menuName, menuDescription, menuType, routePath, sortOrder, visible);
    }

    public Menu(
            Menu parent,
            String menuName,
            String menuDescription,
            MenuType menuType,
            String routePath,
            Long sortOrder,
            Boolean visible
    ) {
        this.parent = parent;
        this.menuName = menuName;
        this.menuDescription = menuDescription;
        this.menuType = menuType;
        this.routePath = routePath;
        this.sortOrder = sortOrder;
        this.visible = visible;
    }

    /**
     * 수정.
     * menuType 은 바꾸지 않는다. 하위 항목이 딸린 그룹을 항목으로 바꾸면
     * 그 항목들이 갈 곳을 잃기 때문에, 유형 변경은 화면에서도 막아둔다.
     */
    public void update(
            Menu parent,
            String menuName,
            String menuDescription,
            String routePath,
            Long sortOrder,
            Boolean visible
    ) {
        this.parent = parent;
        this.menuName = menuName;
        this.menuDescription = menuDescription;
        this.routePath = routePath;
        this.sortOrder = sortOrder;
        this.visible = visible;
    }

    public boolean isGroup() {
        return this.menuType == MenuType.GROUP;
    }

    public Long getParentId() {
        if (this.parent == null) {
            return null;
        }

        return this.parent.getId();
    }
}
