package me.jsjlog.blog.post.service;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.post.domain.DeviceType;
import me.jsjlog.blog.post.domain.PostView;
import me.jsjlog.blog.post.repository.PostViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PostViewRecorder {

	private final PostViewRepository postViewRepository;

	/**
	 * 조회 한 번을 남긴다.
	 *
	 * <p>트랜잭션을 따로 연다. 상세 조회 트랜잭션에 섞이면 이 INSERT 가 실패했을 때 그 트랜잭션이
	 * 롤백만 가능한 상태가 되어, 이미 성공한 글 조회까지 실패로 끝난다.
	 * 실패는 여기서 삼키지 않고 부르는 쪽이 로그를 남기고 넘긴다.</p>
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(Long postId, Long memberId, LocalDateTime viewedAt, DeviceType deviceType) {
		postViewRepository.save(PostView.of(postId, memberId, viewedAt, deviceType));
	}

	/** 30분 안의 회원 재방문은 집계 행을 추가하지 않고 마지막 열람 시각만 갱신한다. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateLastViewedAt(Long postId, Long memberId, LocalDateTime viewedAt) {
		if (memberId == null) {
			return;
		}
		postViewRepository.findFirstByPostIdAndMemberIdOrderByViewedAtDescIdDesc(postId, memberId)
				.ifPresent(view -> postViewRepository.advanceLastViewedAt(view.getId(), viewedAt));
	}
}
