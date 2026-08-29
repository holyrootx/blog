package me.jsjlog.blog.home.service;

import me.jsjlog.blog.home.domain.HomePageTopic;
import me.jsjlog.blog.home.dto.HomePageTopicResponse;
import me.jsjlog.blog.home.repository.HomePageTopicRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomePageTopicService {

    private final HomePageTopicRepository homePageTopicRepository;

    public List<HomePageTopicResponse> getHomePageTopics() {

        Sort sortOrder = Sort.by(Sort.Direction.ASC, "sortOrder", "id");
        List<HomePageTopic> homePageTopics = homePageTopicRepository.findAll(sortOrder);

        List<HomePageTopicResponse> homePageTopicResponseList = new ArrayList<>();
        for (HomePageTopic homePageTopic : homePageTopics) {
            homePageTopicResponseList.add(HomePageTopicResponse.from(homePageTopic));
        }

        return homePageTopicResponseList;
    }

}
