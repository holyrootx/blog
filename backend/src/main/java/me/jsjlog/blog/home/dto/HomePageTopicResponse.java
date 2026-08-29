package me.jsjlog.blog.home.dto;

import me.jsjlog.blog.home.domain.HomePageTopic;

import java.util.ArrayList;
import java.util.List;

public record HomePageTopicResponse(
        Long id,
        String label,
        String title,
        String description,
        List<String> keywords,
        Long sortOrder
) {
    public static HomePageTopicResponse from(HomePageTopic homePageTopic) {
        return new HomePageTopicResponse(
                homePageTopic.getId(),
                homePageTopic.getLabel(),
                homePageTopic.getTitle(),
                homePageTopic.getDescription(),
                parseKeywords(homePageTopic.getKeywords()),
                homePageTopic.getSortOrder()
        );
    }

    /**
     * 콤마로 구분해 저장한 키워드를 목록으로 바꿉니다.
     */
    private static List<String> parseKeywords(String keywords) {
        List<String> keywordList = new ArrayList<>();

        if (keywords == null || keywords.isBlank()) {
            return keywordList;
        }

        String[] splitKeywords = keywords.split(",");
        for (String splitKeyword : splitKeywords) {
            String keyword = splitKeyword.trim();
            if (!keyword.isBlank()) {
                keywordList.add(keyword);
            }
        }

        return keywordList;
    }

}
