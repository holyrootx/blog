package me.jsjlog.blog.post.domain;

/**
 * 게시글의 공개 상태입니다.
 *
 * <p>DB에는 {@code @Enumerated(EnumType.STRING)}으로 이름을 그대로 저장합니다.
 * 순서(ORDINAL)로 저장하면 나중에 값을 추가하거나 순서를 바꿀 때 기존 데이터의 의미가
 * 전부 어긋나기 때문입니다.</p>
 */
public enum PostStatus {

    /** 작성 중. 한 번도 발행하지 않았으므로 {@code publishedAt}이 없습니다. */
    DRAFT,

    /** 공개 발행됨. */
    PUBLISHED,

    /**
     * 발행하기로 했지만 아직 그 시각이 오지 않은 글입니다.
     *
     * <p>{@code PUBLISHED}에 미래 {@code publishedAt}을 두는 방법도 있지만, 그러면 공개 조회가
     * 모두 상태와 시각을 함께 따져야 하고 한 군데라도 빠뜨리면 예약한 글이 새어 나갑니다.
     * 상태로 나눠 두면 {@code status = PUBLISHED} 조건 하나로 충분합니다.</p>
     *
     * <p>시각이 되면 {@code ScheduledPublisher}가 {@code PUBLISHED}로 바꿉니다.</p>
     */
    SCHEDULED,

    /**
     * 발행했다가 다시 내린 글입니다. {@code publishedAt}은 그대로 유지합니다.
     *
     * <p>{@code DRAFT}와 상태를 나누는 이유는 관리자 화면이 "임시저장"과 "비공개"를
     * 서로 다른 탭으로 구분해서 보여주기 때문입니다. {@code DRAFT}이면서
     * {@code publishedAt}이 있는 것으로도 구분할 수 있지만, 그러면 상태의 의미가
     * 두 컬럼의 조합에 숨어버리고 {@code publishedAt}이 지워지는 순간 조용히 깨집니다.</p>
     *
     * <p>공개 API에서는 {@code DRAFT}와 동일하게 취급합니다. {@code PUBLISHED}만 노출됩니다.</p>
     */
    PRIVATE
}
