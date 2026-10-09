package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

import me.jsjlog.blog.common.code.CommonCodeEnums;
import me.jsjlog.blog.common.code.domain.CommonCode;

/**
 * @param managedByEnum 서버 코드(enum)에 있는 코드. 동작이 걸려 있어 화면에서 끌 수 없다
 */
public record AdminCommonCodeResponse(
        String groupCode,
        String code,
        String codeName,
        String description,
        int sortOrder,
        boolean enabled,
        boolean managedByEnum,
        LocalDateTime updatedAt
) {

    public static AdminCommonCodeResponse from(CommonCode code) {
        return new AdminCommonCodeResponse(
                code.getGroupCode(),
                code.getCode(),
                code.getCodeName(),
                code.getDescription(),
                code.getSortOrder(),
                code.isEnabled(),
                CommonCodeEnums.isManagedByEnum(code.getGroupCode(), code.getCode()),
                code.getUpdatedAt()
        );
    }
}
