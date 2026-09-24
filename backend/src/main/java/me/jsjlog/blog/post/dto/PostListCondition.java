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
        String sort,
        String q
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 50;

    /**
     * 검색어 최소 길이.
     *
     * <p>한 글자를 허용하면 "개" 한 자로 거의 모든 글이 걸린다. 걸러 주는 게 없는 검색은
     * 결과가 아니라 목록이라, 찾는 사람에게도 서버에도 쓸모가 없다.</p>
     */
    public static final int MIN_KEYWORD_LENGTH = 2;

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

    /**
     * 실제로 검색에 쓸 말. 쓸 수 없으면 null.
     *
     * <p>앞뒤 공백을 턴 뒤 길이를 본다. 화면에서 걸러 보내지만 여기서도 본다 —
     * 주소창에 {@code ?q=ㄱ} 을 직접 칠 수 있고, 그때 서버가 전체 글을 훑게 두면 안 된다.</p>
     *
     * <p>짧은 검색어를 오류로 막지 않는 이유는, 그게 잘못이 아니라 아직 덜 친 상태이기
     * 때문이다. 검색이 아닌 것으로 보고 평범한 목록을 준다.</p>
     */
    public String keywordOrNull() {
        if (q == null) {
            return null;
        }

        String keyword = q.trim();

        return keyword.length() >= MIN_KEYWORD_LENGTH ? keyword : null;
    }

    public boolean hasKeyword() {
        return keywordOrNull() != null;
    }
}
