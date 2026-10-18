package me.jsjlog.blog.guestbook.dto;

/**
 * 방명록 쓰기·고치기 요청.
 *
 * <p>{@code secret} 이 비어 있으면 공개글로 본다. 체크박스를 안 건드린 요청이 비밀글이 되면
 * 쓴 사람이 의도하지 않은 일이 생긴다.</p>
 */
public record GuestbookWriteRequest(
        String content,
        Boolean secret
) {

    public boolean secretOrDefault() {
        return Boolean.TRUE.equals(secret);
    }
}
