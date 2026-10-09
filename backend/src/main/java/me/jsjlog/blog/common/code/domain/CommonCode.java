package me.jsjlog.blog.common.code.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.jsjlog.blog.common.domain.BaseEntity;

/**
 * 공통 코드 한 줄. 예: (MEMBER_STATUS, WITHDRAWN) → 탈퇴.
 *
 * <p>데이터에는 {@code code} 만 저장하고, 이름이 필요하면 여기서 찾는다.
 * {@code code} 는 한 번 정하면 바꾸지 않는다 — 지난 데이터와 이력이 그 값을 가리킨다.
 * 같은 이유로 지우지 않고 {@code enabled} 를 끈다.</p>
 *
 * <p>그룹으로 외래키를 걸지 않는다. 참조하는 표마다 그룹 칸을 따로 둬야 해서, 코드 값의 검사는
 * 앱이 한다(enum 과 서버 시작 시 대조).</p>
 */
@Getter
@Entity
@IdClass(CommonCodeId.class)
@Table(name = "common_code")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommonCode extends BaseEntity {

    @Id
    @Column(name = "group_code", length = 50)
    private String groupCode;

    @Id
    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "code_name", nullable = false, length = 100)
    private String codeName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    private CommonCode(String groupCode, String code, String codeName, String description, int sortOrder) {
        this.groupCode = groupCode;
        this.code = code;
        this.codeName = codeName;
        this.description = description;
        this.sortOrder = sortOrder;
        this.enabled = true;
    }

    public static CommonCode create(String groupCode, String code, String codeName, String description, int sortOrder) {
        return new CommonCode(groupCode, code, codeName, description, sortOrder);
    }

    /** 코드 값은 바꾸지 않는다. 서버 코드(enum)에 있는 코드의 사용 여부는 부르는 쪽이 막는다 */
    public void change(String codeName, String description, int sortOrder, boolean enabled) {
        this.codeName = codeName;
        this.description = description;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
    }
}
