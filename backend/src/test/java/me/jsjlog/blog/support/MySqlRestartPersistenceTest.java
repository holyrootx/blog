package me.jsjlog.blog.support;

import java.time.LocalDateTime;
import java.util.UUID;
import me.jsjlog.blog.BlogApplication;
import me.jsjlog.blog.admin.dto.AdminPostRequest;
import me.jsjlog.blog.admin.service.AdminPostService;
import me.jsjlog.blog.history.domain.ContentHistory;
import me.jsjlog.blog.history.repository.ContentHistoryRepository;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.domain.MemberStatusCode;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.member.repository.MemberStatusHistoryRepository;
import me.jsjlog.blog.member.service.MemberStatusService;
import me.jsjlog.blog.post.domain.PostStatus;
import me.jsjlog.blog.post.dto.CommentCreateRequest;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.post.service.CommentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** 기존 데이터에 update를 적용하고 애플리케이션을 다시 시작해 같은 DB를 읽는다. */
@EnabledIfEnvironmentVariable(named = "BLOG_TEST_MYSQL_PORT", matches = "[0-9]+")
class MySqlRestartPersistenceTest {
    private static final String CONTENT = "# 재기동 검증\n\n한글 본문과 코드, 이모지 🌱\n```java\nreturn 42;\n```";

    @Test void schemaUpdateAndApplicationRestartKeepPostsCommentsAndMemberRelationships() throws Exception {
        String schema = MySqlTestDatabase.prepare("restart_" + UUID.randomUUID().toString().replace("-", ""));
        try {
            // 감사 작성자 컬럼이 없는 이전 테이블을 준비한다. 운영 SQL 파일은 사용하지 않는다.
            try (var connection = MySqlTestDatabase.connect(schema); var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE category (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50) NOT NULL UNIQUE,"
                        + " sort_order BIGINT NOT NULL, created_at DATETIME(6), updated_at DATETIME(6))");
                statement.execute("INSERT INTO category (name, sort_order) VALUES ('기존 분류', 7)");
            }
            Saved saved;
            long codeCount;
            try (var first = start(schema)) {
                assertUpdate(first);
                JdbcTemplate jdbc = first.getBean(JdbcTemplate.class);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()"
                        + " AND table_name = 'category' AND column_name IN ('created_by', 'updated_by')", Integer.class)).isEqualTo(2);
                assertThat(jdbc.queryForObject("SELECT name FROM category WHERE id = 1", String.class)).isEqualTo("기존 분류");
                codeCount = jdbc.queryForObject("SELECT COUNT(*) FROM common_code", Long.class);
                saved = save(first);
                verify(first, saved);
            }
            // 연결 풀·EntityManager·애플리케이션 캐시를 모두 닫은 뒤 같은 DB로 다시 부팅한다.
            try (var restarted = start(schema)) {
                assertUpdate(restarted);
                verify(restarted, saved);
                assertThat(restarted.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM common_code", Long.class))
                        .isEqualTo(codeCount);
                restarted.getBean(AdminPostService.class).unpublishPost(saved.postId());
                assertThat(restarted.getBean(PostRepository.class).findById(saved.postId()).orElseThrow().getStatus())
                        .isEqualTo(PostStatus.PRIVATE);
            }
        } finally {
            try (var connection = MySqlTestDatabase.connect(""); var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }

    private ConfigurableApplicationContext start(String schema) {
        return new SpringApplicationBuilder(BlogApplication.class).web(WebApplicationType.SERVLET).run(
                "--server.port=0",
                "--server.address=127.0.0.1",
                "--spring.datasource.url=" + MySqlTestDatabase.url(schema),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--spring.datasource.username=" + MySqlTestDatabase.username(),
                "--spring.datasource.password=" + MySqlTestDatabase.password(),
                "--spring.jpa.hibernate.ddl-auto=update",
                "--spring.sql.init.mode=always",
                "--spring.jpa.defer-datasource-initialization=true",
                "--spring.sql.init.data-locations=classpath:db/common-code-seed.sql",
                "--blog.scheduled-publish.interval-ms=3600000");
    }

    private void assertUpdate(ConfigurableApplicationContext context) {
        assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
        assertThat(context.getBean(JdbcTemplate.class).queryForObject("SELECT VERSION()", String.class)).startsWith("8.0.");
    }

    private Saved save(ConfigurableApplicationContext context) {
        var members = context.getBean(MemberRepository.class);
        var memberStatus = context.getBean(MemberStatusService.class);
        var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        long adminId = members.saveAndFlush(Member.ofLocalAdmin("restart-admin", "unused-test-hash", "관리자")).getId();
        long writerId = tx.execute(status -> {
            Member writer = members.save(Member.ofSocial(AuthProvider.GOOGLE, "restart-reader", "독자", "reader@example.test", null));
            memberStatus.recordSignup(writer, LocalDateTime.now());
            return writer.getId();
        });
        var posts = context.getBean(AdminPostService.class);
        long postId = posts.createPost(new AdminPostRequest(null, "보존할 글", 1L, "보존할 요약", CONTENT, null), adminId);
        posts.publishPost(postId, null);
        var comments = context.getBean(CommentService.class);
        long commentId = comments.createComment(postId, new CommentCreateRequest("처음 댓글", null), writerId).id();
        comments.updateComment(commentId, "수정한 댓글", writerId);
        tx.executeWithoutResult(status -> memberStatus.suspend(members.findById(writerId).orElseThrow(), adminId, LocalDateTime.now()));
        return new Saved(adminId, writerId, postId, commentId);
    }

    private void verify(ConfigurableApplicationContext context, Saved saved) {
        var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        tx.executeWithoutResult(status -> {
            var category = context.getBean(CategoryRepository.class).findById(1L).orElseThrow();
            assertThat(category.getName()).isEqualTo("기존 분류");
            assertThat(category.getSortOrder()).isEqualTo(7L);
            var post = context.getBean(PostRepository.class).findById(saved.postId()).orElseThrow();
            assertThat(post.getTitle()).isEqualTo("보존할 글");
            assertThat(post.getExcerpt()).isEqualTo("보존할 요약");
            assertThat(post.getContent()).isEqualTo(CONTENT);
            assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
            assertThat(post.getCategory().getId()).isEqualTo(1L);
            assertThat(post.getAuthor().getId()).isEqualTo(saved.adminId());
            var comment = context.getBean(CommentRepository.class).findById(saved.commentId()).orElseThrow();
            assertThat(comment.getContent()).isEqualTo("수정한 댓글");
            assertThat(comment.getPost().getId()).isEqualTo(saved.postId());
            assertThat(comment.getMember().getId()).isEqualTo(saved.writerId());
            assertThat(context.getBean(ContentHistoryRepository.class)
                    .findByTargetTypeAndTargetIdOrderByIdDesc(ContentHistory.Target.COMMENT, saved.commentId()))
                    .extracting(ContentHistory::getAction).containsExactly(ContentHistory.Action.UPDATE, ContentHistory.Action.CREATE);
            var writer = context.getBean(MemberRepository.class).findById(saved.writerId()).orElseThrow();
            assertThat(writer.getProviderUserId()).isEqualTo("restart-reader");
            assertThat(writer.getStatusCode()).isEqualTo(MemberStatusCode.SUSPENDED);
            assertThat(context.getBean(MemberStatusHistoryRepository.class).findAllByMember_IdOrderByIdAsc(saved.writerId())).hasSize(2);
        });
    }

    private record Saved(long adminId, long writerId, long postId, long commentId) {}
}
