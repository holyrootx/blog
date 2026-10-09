package me.jsjlog.blog.common.code.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.jsjlog.blog.common.domain.BaseEntity;

/**
 * 공통 코드 묶음. 예: MEMBER_STATUS(회원 상태).
 *
 * <p>그룹 코드는 한 번 정하면 바꾸지 않는다 — enum 과 데이터가 가리키는 열쇠라 바꾸면 그 순간 뜻이 끊긴다.
 * 같은 이유로 지우지 않고 {@code enabled} 를 끈다. 이름·설명·사용 여부는 관리 화면에서 고친다.</p>
 */
@Getter
@Entity
@Table(name = "common_code_group")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommonCodeGroup extends BaseEntity {

    @Id
    @Column(name = "group_code", length = 50)
    private String groupCode;

    @Column(name = "group_name", nullable = false, length = 100)
    private String groupName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    private CommonCodeGroup(String groupCode, String groupName, String description) {
        this.groupCode = groupCode;
        this.groupName = groupName;
        this.description = description;
        this.enabled = true;
    }

    public static CommonCodeGroup create(String groupCode, String groupName, String description) {
        return new CommonCodeGroup(groupCode, groupName, description);
    }

    public void change(String groupName, String description, boolean enabled) {
        this.groupName = groupName;
        this.description = description;
        this.enabled = enabled;
    }
}
