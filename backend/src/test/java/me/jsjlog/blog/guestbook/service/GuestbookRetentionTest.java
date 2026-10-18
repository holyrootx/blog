package me.jsjlog.blog.guestbook.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;
import me.jsjlog.blog.guestbook.domain.GuestbookEntry;
import me.jsjlog.blog.guestbook.domain.GuestbookReport;
import me.jsjlog.blog.guestbook.repository.GuestbookEntryRepository;
import me.jsjlog.blog.guestbook.repository.GuestbookReportRepository;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.CommentReportReason;
import me.jsjlog.blog.post.service.CommentRetention;
import me.jsjlog.blog.support.MySqlTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쓴 사람이 지운 방명록 원문은 지운 날부터 6개월 뒤 파기한다. 신고 당시 사본도 같이 지운다.
 *
 * <p>기준 시각을 넘겨 확인한다. 날짜 계산이 달 끝에서 흔들리지 않게 15일로 잡는다.</p>
 */
@SpringBootTest
@Transactional
class GuestbookRetentionTest {

    private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 4, 15, 10, 0);
    private static final LocalDateTime EXPIRES_AT = DELETED_AT.plus(CommentRetention.RETENTION);

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        MySqlTestDatabase.configure(registry, "guestbook_retention");
    }

    @Autowired private GuestbookRetention retention;
    @Autowired private GuestbookEntryRepository entries;
    @Autowired private GuestbookReportRepository reports;
    @Autowired private MemberRepository members;
    @Autowired private EntityManager em;

    private Member writer;
    private Member admin;
    private Member reporter;

    @BeforeEach
    void setUp() {
        writer = members.save(Member.ofSocial(AuthProvider.NAVER, "guest-retention-writer", "손님", null, null));
        reporter = members.save(Member.ofSocial(AuthProvider.NAVER, "guest-retention-reporter", "신고자", null, null));
        admin = members.save(Member.ofLocalAdmin("guest-retention-admin", "encoded-password", "운영자"));
    }

    private GuestbookEntry deletedEntry(String content) {
        GuestbookEntry entry = new GuestbookEntry(writer, content, false);
        entry.reply(admin, "운영자 답글", DELETED_AT.minusDays(1));
        entry = entries.save(entry);
        reports.save(new GuestbookReport(entry, reporter, CommentReportReason.SPAM, null));
        entry.delete(DELETED_AT);
        em.flush();
        return entry;
    }

    @Test
    @DisplayName("기한이 지나면 본문·답글·신고 사본을 비우고 행은 남긴다")
    void purgesAfterRetention() {
        GuestbookEntry entry = deletedEntry("지운 인사");

        assertThat(retention.purgeExpired(EXPIRES_AT.plusSeconds(1))).isEqualTo(1);
        em.clear();

        GuestbookEntry purged = entries.findById(entry.getId()).orElseThrow();
        assertThat(purged.getContent()).isEmpty();
        assertThat(purged.getReplyContent()).isNull();
        assertThat(purged.getReplyMember()).isNull();
        assertThat(purged.isContentPurged()).isTrue();
        assertThat(reports.findByEntryIdOrderByIdAsc(entry.getId()))
                .singleElement()
                .satisfies(report -> assertThat(report.getReportedContent()).isNull());
    }

    @Test
    @DisplayName("기한 전에는 그대로 둔다. 정확히 6개월 된 것도 다음 날 정리한다")
    void keepsBeforeRetention() {
        GuestbookEntry entry = deletedEntry("아직 보관");

        assertThat(retention.purgeExpired(EXPIRES_AT)).isZero();
        em.clear();

        assertThat(entries.findById(entry.getId()).orElseThrow().getContent()).isEqualTo("아직 보관");
    }

    @Test
    @DisplayName("쓴 사람이 지운 뒤 운영자가 가려도 지운 날부터 세어 파기한다 — 가리기로 보관 기간이 늘지 않는다")
    void writerDeletedThenHiddenIsStillPurged() {
        GuestbookEntry entry = deletedEntry("지운 뒤 가린 글");
        entry.hideByAdmin();
        em.flush();

        assertThat(retention.purgeExpired(EXPIRES_AT.plusSeconds(1))).isEqualTo(1);
        em.clear();

        assertThat(entries.findById(entry.getId()).orElseThrow().isContentPurged()).isTrue();
    }

    @Test
    @DisplayName("운영자가 가린 글과 보이는 글은 정리하지 않는다")
    void keepsHiddenAndVisible() {
        GuestbookEntry hidden = entries.save(new GuestbookEntry(writer, "가린 글", false));
        hidden.hideByAdmin();
        GuestbookEntry visible = entries.save(new GuestbookEntry(writer, "보이는 글", false));
        em.flush();

        assertThat(retention.purgeExpired(LocalDateTime.now().plusYears(2))).isZero();
        em.clear();

        assertThat(entries.findById(hidden.getId()).orElseThrow().getContent()).isEqualTo("가린 글");
        assertThat(entries.findById(visible.getId()).orElseThrow().getContent()).isEqualTo("보이는 글");
    }
}
