package me.jsjlog.blog.common.code.domain;

import java.io.Serializable;
import java.util.Objects;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공통 코드의 키. 같은 코드 값이 다른 그룹에도 있을 수 있어 그룹과 함께 쓴다 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommonCodeId implements Serializable {

    private String groupCode;
    private String code;

    public CommonCodeId(String groupCode, String code) {
        this.groupCode = groupCode;
        this.code = code;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CommonCodeId id
                && Objects.equals(groupCode, id.groupCode)
                && Objects.equals(code, id.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupCode, code);
    }
}
