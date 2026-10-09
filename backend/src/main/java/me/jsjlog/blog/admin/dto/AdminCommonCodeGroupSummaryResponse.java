package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

import me.jsjlog.blog.common.code.CommonCodeEnums;
import me.jsjlog.blog.common.code.domain.CommonCodeGroup;

/**
 * @param managedByEnum 서버 코드(enum)가 쓰는 그룹. 코드 추가와 사용 여부 변경은 개발로만 한다
 */
public record AdminCommonCodeGroupSummaryResponse(
        String groupCode,
        String groupName,
        String description,
        boolean enabled,
        boolean managedByEnum,
        long codeCount,
        LocalDateTime updatedAt
) {

    public static AdminCommonCodeGroupSummaryResponse of(CommonCodeGroup group, long codeCount) {
        return new AdminCommonCodeGroupSummaryResponse(
                group.getGroupCode(),
                group.getGroupName(),
                group.getDescription(),
                group.isEnabled(),
                CommonCodeEnums.isEnumGroup(group.getGroupCode()),
                codeCount,
                group.getUpdatedAt()
        );
    }
}
