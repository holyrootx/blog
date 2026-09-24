package me.jsjlog.blog.admin.dto;

/** 조치할 때 함께 남기는 메모. 없어도 된다 */
public record AdminCommentModerationRequest(String reason) {
}
