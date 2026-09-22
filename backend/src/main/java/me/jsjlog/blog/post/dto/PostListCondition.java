package me.jsjlog.blog.post.dto;

/**
 * 공개 글 목록 조회조건.
 *
 * <p>관리자 목록과 달리 상태를 받지 않는다. 공개 화면은 발행되고 시각이 지난 글만 보여주며,
 * 그 판단을 요청이 바꿀 수 있으면 주소를 고쳐 비공개 글을 꺼내 볼 수 있다.</p>
 *
 * <p>page 는 0부터다. 화면은 1부터 세므로 그 변환은 프론트의 API 함수 경계에서 한다.</p>
 */
public record PostListCondition(
        Integer page,
        Integer size,
        Long categoryId,
        String sort
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 50;

    public static final String SORT_LATEST = "latest";
    public static final String SORT_POPULAR = "popular";

    public int pageOrDefault() {
        return page == null || page < 0 ? DEFAULT_PAGE : page;
    }

    public int sizeOrDefault() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }

        return Math.min(size, MAX_SIZE);
    }

    /** 모르는 값이 오면 최신순이다. 오류로 막을 만한 일이 아니다 */
    public String sortOrDefault() {
        return SORT_POPULAR.equalsIgnoreCase(sort) ? SORT_POPULAR : SORT_LATEST;
    }

    public boolean isPopular() {
        return SORT_POPULAR.equals(sortOrDefault());
    }
}
