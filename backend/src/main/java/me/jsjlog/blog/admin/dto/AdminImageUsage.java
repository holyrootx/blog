package me.jsjlog.blog.admin.dto;

/**
 * 이미지가 쓰이는 자리 한 곳.
 *
 * where 는 화면이 문구를 고를 때 쓴다. 글이면 title 에 글 제목이 들어가고,
 * 대문과 프로필은 한 곳뿐이라 title 이 없다.
 */
public record AdminImageUsage(String url, Where where, String title) {

    public enum Where {
        POST_CONTENT,
        POST_THUMBNAIL,
        HOME_HERO,
        PROFILE
    }
}
