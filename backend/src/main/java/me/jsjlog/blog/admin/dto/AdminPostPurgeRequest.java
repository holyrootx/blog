package me.jsjlog.blog.admin.dto;

import java.time.LocalDateTime;

/** 관리자가 확인한 제목과 삭제 시각. 복구 후 다시 삭제된 글에 오래된 확인 요청을 적용하지 않는다. */
public record AdminPostPurgeRequest(String title, LocalDateTime deletedAt) {
}
