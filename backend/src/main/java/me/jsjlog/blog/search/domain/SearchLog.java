package me.jsjlog.blog.search.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사람들이 무엇을 찾았는지.
 *
 * <p>나중에 인기 검색어나 검색어 추천을 하려면 지금부터 쌓여 있어야 한다. 검색어 추천은
 * 로그가 있어야 비로소 가능한 기능이라, 기능을 만들 때 시작하면 그때부터 또 기다려야 한다.</p>
 *
 * <p><b>누가 찾았는지는 남기지 않는다.</b> 회원 번호도 IP 도 두지 않는다. 쌓는 목적이
 * "무엇을 찾는가" 이지 "누가 찾는가" 가 아니고, 검색어는 때때로 사적인 것이 섞인다.
 * 필요해진 적도 없는 정보를 미리 모아 두면 지킬 것만 늘어난다.</p>
 *
 * <p>고쳐 쓰지 않고 쌓기만 하므로 {@code BaseEntity} 를 물려받지 않는다 —
 * 수정자·수정시각 칸이 영원히 비어 있게 된다.</p>
 */
@Getter
@Entity
@Table(name = "search_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchLog {

    /** 검색어 저장 한도. 넘으면 자른다 — 주소창에 긴 문자열을 넣어 보내는 것만으로 저장이 깨지면 안 된다 */
    public static final int MAX_KEYWORD_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keyword", nullable = false, length = MAX_KEYWORD_LENGTH)
    private String keyword;

    /** 몇 건 나왔는지. 0 인 검색어가 사람들이 기대했는데 없는 글이다 */
    @Column(name = "result_count", nullable = false)
    private long resultCount;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    private SearchLog(String keyword, long resultCount, LocalDateTime searchedAt) {
        this.keyword = keyword;
        this.resultCount = resultCount;
        this.searchedAt = searchedAt;
    }

    public static SearchLog of(String keyword, long resultCount) {
        return new SearchLog(trim(keyword), resultCount, LocalDateTime.now());
    }

    private static String trim(String keyword) {
        if (keyword == null) {
            return "";
        }

        return keyword.length() <= MAX_KEYWORD_LENGTH
                ? keyword
                : keyword.substring(0, MAX_KEYWORD_LENGTH);
    }
}
