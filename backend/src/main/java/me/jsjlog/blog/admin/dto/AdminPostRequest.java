package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

/**
 * 글 작성·수정 요청.
 *
 * status 를 받지 않는다. 발행은 별도 엔드포인트다 —
 * 저장할 때마다 발행 상태가 바뀌면 "임시저장 눌렀는데 발행됐다" 같은 사고가 난다.
 */
public record AdminPostRequest(
        // 화면이 불러왔던 시점의 값. 등록에는 쓰지 않는다
        LocalDateTime updatedAt,
        String title,
        Long categoryId,
        String excerpt,
        String content,
        String thumbnailImageUrl
) {
}
