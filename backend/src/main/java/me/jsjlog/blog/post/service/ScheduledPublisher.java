package me.jsjlog.blog.post.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.post.repository.PostRepository;

/**
 * 예약한 글을 시각이 되면 공개로 바꾼다.
 *
 * 메모리에 타이머를 두지 않고 매번 DB 를 보고 판단한다. 타이머를 쓰면 서버가 재시작되는 순간
 * 예약이 통째로 사라지고, 그 사이에 지나간 시각은 영영 오지 않는다.
 *
 * 1분마다 도는 것으로 충분하다. 분 단위로 예약하는 글이 1분 늦게 공개되는 것은 문제가 아니고,
 * 더 자주 돌아 봐야 대부분 빈 결과만 확인한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledPublisher {

    private final PostRepository postRepository;

    @Transactional
    @Scheduled(fixedDelayString = "${blog.scheduled-publish.interval-ms:60000}")
    public void publishDuePosts() {
        List<Post> due = postRepository.findDueScheduledPosts(LocalDateTime.now());

        if (due.isEmpty()) {
            return;
        }

        List<Long> publishedIds = new ArrayList<>();
        for (Post post : due) {
            try {
                PostPublicationPolicy.checkPublishable(post.getContent(), post.getExcerpt());
            } catch (BlogException invalid) {
                // 이전 버전이나 직접 데이터 수정으로 생긴 불완전한 예약은 공개하지 않는다.
                // 다른 정상 예약의 발행은 계속하고, 관리자가 수정하거나 취소할 수 있게 남긴다.
                log.warn("[예약 발행] 글 {} 공개 조건 미충족: {}", post.getId(), invalid.getMessage());
                continue;
            }
            post.publish(post.getPublishedAt());
            publishedIds.add(post.getId());
        }

        // 예약한 글이 실제로 나갔다는 사실은 남겨 둔다. 안 나갔을 때 되짚을 곳이 필요하다
        log.info("[예약 발행] {}건 공개 전환: {}", publishedIds.size(), publishedIds);
    }
}
