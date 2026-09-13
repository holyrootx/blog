package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

/**
 * 관리자 카테고리 목록 응답.
 * 공개용 CategoryResponse 와 달리 글 수(postCount)가 붙는다.
 * 글이 달린 카테고리는 삭제할 수 없으므로 화면에서 판단할 근거가 필요하다.
 */
public record AdminCategoryResponse(
        Long id,
        String name,
        Long sortOrder,
        Long postCount,
        // 수정 충돌 감지용. 화면이 불러온 값을 저장 때 그대로 돌려보낸다
        LocalDateTime updatedAt
) {
}
