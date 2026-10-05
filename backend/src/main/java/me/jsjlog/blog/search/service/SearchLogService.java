package me.jsjlog.blog.search.service;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.search.domain.SearchLog;
import me.jsjlog.blog.search.repository.SearchLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchLogService {

    private final SearchLogRepository searchLogRepository;

    /**
     * 검색 한 번을 남긴다.
     *
     * <p>목록 조회는 읽기 전용 트랜잭션 안에서 도는데 여기서는 써야 하므로 트랜잭션을 새로 연다.</p>
     *
     * <p>실패는 여기서 삼키지 않는다. 저장이 실패하면 이 트랜잭션은 이미 롤백만 가능한 상태라
     * 안에서 잡아도 커밋할 때 다시 터진다. 실패를 흘려보내 깨끗이 롤백하고, 삼키는 일은 부르는 쪽이 한다.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String keyword, long resultCount) {
        searchLogRepository.save(SearchLog.of(keyword, resultCount));
    }
}
