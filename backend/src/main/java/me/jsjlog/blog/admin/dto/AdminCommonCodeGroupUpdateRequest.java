package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

/**
 * 공통 코드 그룹 수정. 그룹 코드는 받지 않는다.
 *
 * @param enabled 비우면 그대로 둔다. 서버 코드(enum)가 쓰는 그룹은 끌 수 없다
 */
public record AdminCommonCodeGroupUpdateRequest(
        // 화면이 불러왔던 시점의 값. 지금 서버 값과 다르면 그 사이 누가 고친 것이다
        LocalDateTime updatedAt,
        String groupName,
        String description,
        Boolean enabled
) {
}
