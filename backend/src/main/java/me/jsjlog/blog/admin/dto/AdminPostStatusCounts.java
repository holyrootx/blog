package me.jsjlog.blog.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 상태 필터에 붙는 건수.
 * status 필터와 무관하게 항상 전체 기준이다 — 발행 탭을 보고 있어도
 * 임시저장이 몇 개인지 보여야 하기 때문이다.
 *
 * private 은 자바 예약어라 필드명을 쓸 수 없어 JSON 이름만 맞춘다.
 */
public record AdminPostStatusCounts(
        long all,
        long published,
        long scheduled,
        @JsonProperty("private") long privateCount,
        long draft
) {
}
