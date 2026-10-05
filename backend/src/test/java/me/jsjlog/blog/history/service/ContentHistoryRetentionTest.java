package me.jsjlog.blog.history.service;

import me.jsjlog.blog.history.repository.ContentHistoryRepository;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.dto.CommentCreateRequest;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.post.service.CommentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ContentHistoryRetentionTest {

    @Autowired
    private ContentHistoryRetention retention;

    @Autowired
    private ContentHistoryRepository historyRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CommentService commentService;

    private void writeComment() {
        Category category = categoryRepository.save(new Category("보관 기간", 932L));
        Post post = new Post("보관 기간 글", "요약", "본문", category, null, null);
        post.publish(LocalDateTime.now().minusDays(1));
        post = postRepository.save(post);

        Member member = memberRepository.save(Member.ofSocial(
                AuthProvider.NAVER, "retention-writer", "글쓴이", null, null));
        commentService.createComment(post.getId(), new CommentCreateRequest("남길 말", null), member.getId());
    }

    @Test
    @DisplayName("6개월이 안 된 기록은 남긴다")
    void keepsRecentHistory() {
        writeComment();
        long before = historyRepository.count();

        assertThat(retention.purgeExpired(LocalDateTime.now().plusMonths(6).minusDays(1))).isZero();
        assertThat(historyRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("6개월이 지난 기록은 지운다")
    void deletesExpiredHistory() {
        writeComment();
        long before = historyRepository.count();

        assertThat(retention.purgeExpired(LocalDateTime.now().plusMonths(6).plusDays(1))).isEqualTo(before);
        assertThat(historyRepository.count()).isZero();
    }
}
