package me.jsjlog.blog.common.upload;

/**
 * 저장이 끝난 이미지가 어디에 있는지.
 *
 * key 와 url 을 둘 다 들고 있는 이유는 쓰임이 달라서다.
 * 지울 때 필요한 건 key 고, 글 본문에 박히는 건 url 이다.
 */
public record StoredImage(String storageKey, String url) {
}
