package me.jsjlog.blog.home.service;

import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.home.domain.HomePageTopic;
import me.jsjlog.blog.home.domain.HomePageTopicSection;
import me.jsjlog.blog.home.dto.HomePageTopicSectionResponse;
import me.jsjlog.blog.home.dto.HomePageTopicSettingsResponse;
import me.jsjlog.blog.home.dto.HomePageTopicResponse;
import me.jsjlog.blog.home.dto.UpdateHomePageTopicRequest;
import me.jsjlog.blog.home.dto.UpdateHomePageTopicsRequest;
import me.jsjlog.blog.home.repository.HomePageTopicRepository;
import me.jsjlog.blog.home.repository.HomePageTopicSectionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomePageTopicService {

    private static final long DEFAULT_SECTION_ID = 1L;
    private static final int MAX_TOPIC_COUNT = 6;
    private static final int MAX_KEYWORD_COUNT = 6;
    private static final int LABEL_MAX_LENGTH = 50;
    private static final int TITLE_MAX_LENGTH = 100;
    private static final int DESCRIPTION_MAX_LENGTH = 500;
    private static final int KEYWORDS_MAX_LENGTH = 255;
    private static final int KEYWORD_MAX_LENGTH = 30;

    private static final HomePageTopicSectionResponse DEFAULT_SECTION =
            new HomePageTopicSectionResponse(
                    DEFAULT_SECTION_ID,
                    "여기서 다루는 이야기",
                    "앞으로 이런 글을 쓸거에요.",
                    "쓰는 중",
                    "아직 글이 많지 않아요. 하나씩 채워가는 중이에요."
            );

    private final HomePageTopicRepository homePageTopicRepository;
    private final HomePageTopicSectionRepository homePageTopicSectionRepository;

    @Transactional(readOnly = true)
    public List<HomePageTopicResponse> getHomePageTopics() {
        Sort sortOrder = Sort.by(Sort.Direction.ASC, "sortOrder", "id");
        return homePageTopicRepository.findAll(sortOrder)
                .stream()
                .map(HomePageTopicResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public HomePageTopicSectionResponse getHomePageTopicSection() {
        return homePageTopicSectionRepository.findById(DEFAULT_SECTION_ID)
                .map(HomePageTopicSectionResponse::from)
                .orElse(DEFAULT_SECTION);
    }

    @Transactional
    public HomePageTopicSettingsResponse updateHomePageTopics(UpdateHomePageTopicsRequest request) {
        validate(request);

        HomePageTopicSection section = findOrCreateSection(request.sectionId());
        section.update(
                request.title().trim(),
                request.intro().trim(),
                request.noteBadge().trim(),
                request.note().trim()
        );
        homePageTopicSectionRepository.save(section);

        Map<Long, HomePageTopic> remainingTopics = new HashMap<>();
        homePageTopicRepository.findAll().forEach(topic -> remainingTopics.put(topic.getId(), topic));

        List<HomePageTopic> updatedTopics = new java.util.ArrayList<>();
        for (int index = 0; index < request.topics().size(); index++) {
            UpdateHomePageTopicRequest item = request.topics().get(index);
            HomePageTopic topic = item.id() == null
                    ? new HomePageTopic("", "", "", null, (long) index + 1)
                    : removeExistingTopic(remainingTopics, item.id());

            topic.update(
                    item.label().trim(),
                    item.title().trim(),
                    item.description().trim(),
                    joinKeywords(item.keywords()),
                    (long) index + 1
            );
            updatedTopics.add(topic);
        }

        homePageTopicRepository.deleteAll(remainingTopics.values());
        List<HomePageTopicResponse> responses = homePageTopicRepository.saveAll(updatedTopics)
                .stream()
                .map(HomePageTopicResponse::from)
                .toList();

        return new HomePageTopicSettingsResponse(
                HomePageTopicSectionResponse.from(section),
                responses
        );
    }

    private HomePageTopicSection findOrCreateSection(Long sectionId) {
        if (sectionId != null && sectionId != DEFAULT_SECTION_ID) {
            throw new BlogException(ErrorCode.HOME_TOPIC_SECTION_NOT_FOUND);
        }

        return homePageTopicSectionRepository.findById(DEFAULT_SECTION_ID)
                .orElseGet(() -> new HomePageTopicSection(
                        DEFAULT_SECTION.title(),
                        DEFAULT_SECTION.intro(),
                        DEFAULT_SECTION.noteBadge(),
                        DEFAULT_SECTION.note()
                ));
    }

    private HomePageTopic removeExistingTopic(Map<Long, HomePageTopic> topics, Long topicId) {
        HomePageTopic topic = topics.remove(topicId);
        if (topic == null) {
            throw new BlogException(ErrorCode.HOME_TOPIC_NOT_FOUND);
        }
        return topic;
    }

    private void validate(UpdateHomePageTopicsRequest request) {
        if (request == null
                || !StringUtils.hasText(request.title())
                || !StringUtils.hasText(request.intro())
                || !StringUtils.hasText(request.noteBadge())
                || !StringUtils.hasText(request.note())
                || request.topics() == null) {
            throw new BlogException(ErrorCode.HOME_TOPIC_REQUIRED_VALUE_MISSING);
        }

        if (request.title().trim().length() > TITLE_MAX_LENGTH
                || request.intro().trim().length() > KEYWORDS_MAX_LENGTH
                || request.noteBadge().trim().length() > LABEL_MAX_LENGTH
                || request.note().trim().length() > KEYWORDS_MAX_LENGTH) {
            throw new BlogException(ErrorCode.HOME_TOPIC_VALUE_TOO_LONG);
        }

        if (request.topics().size() > MAX_TOPIC_COUNT) {
            throw new BlogException(ErrorCode.HOME_TOPIC_SIZE_LIMIT_EXCEEDED);
        }

        Set<Long> ids = new HashSet<>();
        for (UpdateHomePageTopicRequest topic : request.topics()) {
            validateTopic(topic);
            if (topic.id() != null && !ids.add(topic.id())) {
                throw new BlogException(ErrorCode.INVALID_INPUT);
            }
        }
    }

    private void validateTopic(UpdateHomePageTopicRequest topic) {
        if (topic == null
                || !StringUtils.hasText(topic.label())
                || !StringUtils.hasText(topic.title())
                || !StringUtils.hasText(topic.description())
                || topic.keywords() == null) {
            throw new BlogException(ErrorCode.HOME_TOPIC_REQUIRED_VALUE_MISSING);
        }

        if (topic.label().trim().length() > LABEL_MAX_LENGTH
                || topic.title().trim().length() > TITLE_MAX_LENGTH
                || topic.description().trim().length() > DESCRIPTION_MAX_LENGTH
                || topic.keywords().size() > MAX_KEYWORD_COUNT
                || topic.keywords().stream().anyMatch(keyword -> keyword == null
                        || !StringUtils.hasText(keyword)
                        || keyword.trim().length() > KEYWORD_MAX_LENGTH)
                || joinKeywords(topic.keywords()).length() > KEYWORDS_MAX_LENGTH) {
            throw new BlogException(ErrorCode.HOME_TOPIC_VALUE_TOO_LONG);
        }
    }

    private String joinKeywords(List<String> keywords) {
        String joined = keywords.stream()
                .map(String::trim)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");

        return joined.isBlank() ? null : joined;
    }

}
