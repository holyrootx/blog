package me.jsjlog.blog.admin.dto;

public record AdminCommentSearchCondition(
        Integer page,
        Integer size,
        AdminCommentFilter status,
        String keyword
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public int pageOrDefault() {
        return page == null || page < 0 ? DEFAULT_PAGE : page;
    }

    public int sizeOrDefault() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }

        return Math.min(size, MAX_SIZE);
    }

    public AdminCommentFilter statusOrDefault() {
        return status == null ? AdminCommentFilter.ALL : status;
    }
}
