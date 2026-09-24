package me.jsjlog.blog.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.jsjlog.blog.search.domain.SearchLog;
import me.jsjlog.blog.search.repository.SearchLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchLogService {

    private final SearchLogRepository searchLogRepository;

    /**
     * 검색 한 번을 남긴다.
     *
     * <p>목록 조회는 읽기 전용 트랜잭션 안에서 도는데 여기서는 써야 하므로 트랜잭션을 새로 연다.</p>
     *
     * <p>실패해도 삼킨다. 기록은 나중에 보려고 쌓는 덤이고, 이것 때문에 검색 결과가 안 나오면
     * 본말이 뒤집힌다. 대신 로그로 남겨 두어 조용히 비어 가는 일은 없게 한다.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String keyword, long resultCount) {
        try {
            searchLogRepository.save(SearchLog.of(keyword, resultCount));
        } catch (RuntimeException e) {
            log.warn("[record] 검색 기록 실패. keyword={}, resultCount={}", keyword, resultCount, e);
        }
    }
}
