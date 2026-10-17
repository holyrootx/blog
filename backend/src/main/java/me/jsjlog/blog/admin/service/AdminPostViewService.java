package me.jsjlog.blog.admin.service;

import lombok.RequiredArgsConstructor;
import me.jsjlog.blog.admin.dto.AdminPostViewListResponse;
import me.jsjlog.blog.admin.dto.AdminPostViewResponse;
import me.jsjlog.blog.admin.dto.AdminPostViewSearchCondition;
import me.jsjlog.blog.admin.repository.AdminPostViewQueryRepository;
import me.jsjlog.blog.common.code.CommonCodes;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPostViewService {

    private final PostRepository postRepository;
    private final AdminPostViewQueryRepository postViewQueryRepository;
    private final CommonCodes commonCodes;

    /**
     * 글 하나의 조회 기록.
     *
     * <p>휴지통의 글도 볼 수 있다. 기록은 글이 내려가도 지우지 않고, 언제 누가 봤는지는
     * 내린 뒤에 더 궁금해지는 일이다.</p>
     */
    @Transactional(readOnly = true)
    public AdminPostViewListResponse getPostViews(Long postId, AdminPostViewSearchCondition condition) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BlogException(ErrorCode.POST_NOT_FOUND));

        long total = postViewQueryRepository.countViews(postId, condition.viewer());
        int size = condition.sizeOrDefault();

        return new AdminPostViewListResponse(
                post.getId(),
                post.getTitle(),
                post.getViews(),
                postViewQueryRepository.findRecordingStartedAt(),
                postViewQueryRepository.countByViewer(postId),
                postViewQueryRepository.getViews(postId, condition).stream().map(this::withStatusName).toList(),
                condition.pageOrDefault(),
                size,
                total,
                total == 0 ? 0 : (int) Math.ceil((double) total / size)
        );
    }

    /** 상태 이름은 공통 코드 MEMBER_STATUS 에서 가져온다. 회원 관리 화면과 같은 이름이 보인다 */
    private AdminPostViewResponse withStatusName(AdminPostViewResponse row) {
        return row.withMemberStatusName(commonCodes.nameOf(row.memberStatus()));
    }
}
