package me.jsjlog.blog.admin.dto;

/**
 * 글 하나의 조회 기록 건수. 기록을 시작한 뒤의 건수다.
 *
 * <p>비회원은 사람 수가 아니라 방문 건수다. 같은 사람이 브라우저를 바꾸면 따로 센다.
 * 스스로 봇이라고 밝힌 요청은 비회원에서 빼서 {@code bot} 으로 센다.</p>
 */
public record AdminPostViewCounts(
        long all,
        long member,
        long admin,
        long anonymous,
        long bot
) {
}
