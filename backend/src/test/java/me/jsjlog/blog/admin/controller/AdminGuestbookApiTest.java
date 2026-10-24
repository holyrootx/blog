package me.jsjlog.blog.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.guestbook.domain.GuestbookEntry;
import me.jsjlog.blog.guestbook.domain.GuestbookReport;
import me.jsjlog.blog.guestbook.repository.GuestbookEntryRepository;
import me.jsjlog.blog.guestbook.repository.GuestbookReportRepository;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.notification.domain.Notification;
import me.jsjlog.blog.notification.domain.NotificationType;
import me.jsjlog.blog.notification.repository.NotificationRepository;
import me.jsjlog.blog.post.domain.CommentReportReason;
import me.jsjlog.blog.support.MySqlTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 방명록. 답글·가리기·신고 판단.
 *
 * <p>익명은 401, 일반 회원은 403, 운영자만 통과해야 한다. 상태를 바꾸는 요청은 CSRF 토큰도 본다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminGuestbookApiTest {

    private static final String ADMIN_GUESTBOOK = "/api/v1/admin/blog/guestbook";

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        MySqlTestDatabase.configure(registry, "admin_guestbook");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private MemberRepository memberRepository;
    @Autowired private GuestbookEntryRepository entryRepository;
    @Autowired private GuestbookReportRepository reportRepository;
    @Autowired private EntityManager em;
    @Autowired private NotificationRepository notificationRepository;

    private Member writer;
    private Member reporter;
    private MemberPrincipal admin;
    private GuestbookEntry entry;

    @BeforeEach
    void setUp() {
        writer = memberRepository.save(Member.ofSocial(AuthProvider.NAVER, "admin-guest-writer", "손님", null, null));
        reporter = memberRepository.save(Member.ofSocial(AuthProvider.NAVER, "admin-guest-reporter", "신고자", null, null));
        admin = MemberPrincipal.ofLocal(memberRepository.save(
                Member.ofLocalAdmin("admin-guest-admin", "encoded-password", "운영자")));
        entry = entryRepository.save(new GuestbookEntry(writer, "비밀 인사", true));
        reportRepository.save(new GuestbookReport(entry, reporter, CommentReportReason.ABUSE, null));
    }

    private RequestPostProcessor asAdmin() {
        return user(admin);
    }

    private String url(String suffix) {
        return ADMIN_GUESTBOOK + "/" + entry.getId() + suffix;
    }

    @Test
    @DisplayName("익명은 401, 일반 회원은 403, 운영자는 비밀글 본문까지 본다")
    void requiresAdmin() throws Exception {
        mockMvc.perform(get(ADMIN_GUESTBOOK)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ADMIN_GUESTBOOK).with(user(MemberPrincipal.ofSocial(writer))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(ADMIN_GUESTBOOK).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].content").value("비밀 인사"))
                .andExpect(jsonPath("$.data.items[0].secret").value(true))
                .andExpect(jsonPath("$.data.items[0].unhandledReportCount").value(1))
                .andExpect(jsonPath("$.data.statusCounts.all").value(1))
                .andExpect(jsonPath("$.data.statusCounts.reported").value(1));
    }

    @Test
    @DisplayName("일반 회원은 답글을 달 수 없다")
    void memberCannotReply() throws Exception {
        mockMvc.perform(put(url("/reply")).with(user(MemberPrincipal.ofSocial(writer))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"내가 운영자"}
                                """))
                .andExpect(status().isForbidden());

        assertThat(entryRepository.findById(entry.getId()).orElseThrow().hasReply()).isFalse();
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 운영자라도 막는다")
    void rejectsWithoutCsrf() throws Exception {
        mockMvc.perform(put(url("/visibility")).with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hidden":true}
                                """))
                .andExpect(status().isForbidden());

        assertThat(entryRepository.findById(entry.getId()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("답글은 글마다 하나다. 다시 쓰면 바뀌고, 지울 수 있다")
    void repliesOncePerEntry() throws Exception {
        mockMvc.perform(put(url("/reply")).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"  고마워요  "}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put(url("/reply")).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"고마워요!"}
                                """))
                .andExpect(status().isOk());

        GuestbookEntry replied = entryRepository.findById(entry.getId()).orElseThrow();
        assertThat(replied.getReplyContent()).isEqualTo("고마워요!");
        assertThat(replied.getReplyMember().getId()).isEqualTo(admin.getId());

        // 비밀글이라 다른 회원에게는 답글도 안 보인다
        mockMvc.perform(get("/api/v1/blog/guestbook").with(user(MemberPrincipal.ofSocial(reporter))))
                .andExpect(jsonPath("$.data.items[0].reply").doesNotExist());
        mockMvc.perform(get("/api/v1/blog/guestbook").with(user(MemberPrincipal.ofSocial(writer))))
                .andExpect(jsonPath("$.data.items[0].reply.content").value("고마워요!"));

        mockMvc.perform(delete(url("/reply")).with(asAdmin()).with(csrf()))
                .andExpect(status().isOk());
        assertThat(entryRepository.findById(entry.getId()).orElseThrow().hasReply()).isFalse();
    }

    @Test
    @DisplayName("처음 답글을 달면 쓴 사람에게 알림이 가고, 고칠 때는 다시 가지 않는다")
    void notifiesWriterOnFirstReplyOnly() throws Exception {
        for (String content : new String[] {"첫 답글", "고친 답글"}) {
            mockMvc.perform(put(url("/reply")).with(asAdmin()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"" + content + "\"}"))
                    .andExpect(status().isOk());
        }

        assertThat(notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipient().getId().equals(writer.getId()))
                .map(Notification::getType)
                .toList())
                .containsExactly(NotificationType.GUESTBOOK_REPLIED);

        // 지웠다가 새로 달면 새 답이라 다시 알린다
        mockMvc.perform(delete(url("/reply")).with(asAdmin()).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(put(url("/reply")).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"다시 단 답글"}
                                """))
                .andExpect(status().isOk());

        assertThat(notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipient().getId().equals(writer.getId())))
                .hasSize(2);
    }

    @Test
    @DisplayName("빈 답글과 500자를 넘는 답글은 받지 않는다")
    void validatesReply() throws Exception {
        mockMvc.perform(put(url("/reply")).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_REPLY_REQUIRED"));

        mockMvc.perform(put(url("/reply")).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + "가".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_REPLY_TOO_LONG"));
    }

    @Test
    @DisplayName("가리면 공개 목록에서 빠지고 걸린 신고가 처리된다. 되돌리면 다시 보인다")
    void hidesAndRestores() throws Exception {
        mockMvc.perform(put(url("/visibility")).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hidden":true}
                                """))
                .andExpect(status().isOk());

        GuestbookEntry hidden = entryRepository.findById(entry.getId()).orElseThrow();
        assertThat(hidden.isDeleted()).isTrue();
        assertThat(hidden.isHiddenByAdmin()).isTrue();
        assertThat(reportRepository.findByEntryIdAndHandledAtIsNull(entry.getId())).isEmpty();

        mockMvc.perform(get("/api/v1/blog/guestbook").with(user(MemberPrincipal.ofSocial(reporter))))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mockMvc.perform(get(ADMIN_GUESTBOOK).param("status", "HIDDEN").with(asAdmin()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].deletedByWriter").value(false))
                .andExpect(jsonPath("$.data.statusCounts.reported").value(0));

        mockMvc.perform(put(url("/visibility")).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hidden":false}
                                """))
                .andExpect(status().isOk());

        assertThat(entryRepository.findById(entry.getId()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("가리면 쓴 사람에게 알림이 간다. 되돌릴 때는 안 가고, 다시 가리면 다시 간다")
    void notifiesWriterWhenHidden() throws Exception {
        for (boolean hidden : new boolean[] {true, false, true}) {
            mockMvc.perform(put(url("/visibility")).with(asAdmin()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"hidden\":" + hidden + "}"))
                    .andExpect(status().isOk());
        }

        assertThat(notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipient().getId().equals(writer.getId()))
                .map(Notification::getType)
                .toList())
                .containsExactly(NotificationType.GUESTBOOK_HIDDEN, NotificationType.GUESTBOOK_HIDDEN);
    }

    @Test
    @DisplayName("쓴 사람이 이미 지운 글을 가리면 알리지 않는다 — 쓴 사람에게 새로 생긴 일이 아니다")
    void hidingDeletedEntryIsSilent() throws Exception {
        entry.delete(LocalDateTime.now());

        mockMvc.perform(put(url("/visibility")).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hidden":true}
                                """))
                .andExpect(status().isOk());

        assertThat(notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipient().getId().equals(writer.getId())))
                .isEmpty();

        // 지운 뒤 가린 글을 "운영자가 가린 글" 로 보고 되살리면 안 된다
        mockMvc.perform(put(url("/visibility")).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hidden":false}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_DELETED_BY_WRITER"));
        assertThat(entryRepository.findById(entry.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("문제 없음으로 넘기면 글은 그대로이고 신고 목록에서 내려간다")
    void dismissesReports() throws Exception {
        mockMvc.perform(post(url("/reports/dismiss")).with(asAdmin()).with(csrf()))
                .andExpect(status().isOk());

        assertThat(entryRepository.findById(entry.getId()).orElseThrow().isDeleted()).isFalse();
        mockMvc.perform(get(ADMIN_GUESTBOOK).param("status", "REPORTED").with(asAdmin()))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("상세에는 신고 당시 본문이 있고 신고한 사람은 없다")
    void detailShowsReportsWithoutReporter() throws Exception {
        entry.update("신고 뒤에 고친 글", true);

        mockMvc.perform(get(url("")).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entry.content").value("신고 뒤에 고친 글"))
                .andExpect(jsonPath("$.data.reports.length()").value(1))
                .andExpect(jsonPath("$.data.reports[0].reportedContent").value("비밀 인사"))
                .andExpect(jsonPath("$.data.reports[0].reasonLabel").value("욕설·비방"))
                .andExpect(jsonPath("$.data.reports[0].nickname").doesNotExist())
                .andExpect(jsonPath("$.data.reports[0].memberId").doesNotExist());
    }

    @Test
    @DisplayName("쓴 사람이 지운 글은 목록에 지움으로 남고 파기 예정일이 보인다")
    void showsDeletedEntryWithRetention() throws Exception {
        LocalDateTime deletedAt = LocalDateTime.now().minusDays(1);
        entry.delete(deletedAt);

        mockMvc.perform(get(ADMIN_GUESTBOOK).param("status", "DELETED").with(asAdmin()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].hiddenByAdmin").value(false));
        mockMvc.perform(get(url("")).with(asAdmin()))
                .andExpect(jsonPath("$.data.retainedUntil").isNotEmpty());

        // 지운 글에 답하면 그 답글은 아무도 못 본다
        mockMvc.perform(put(url("/reply")).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"늦은 답"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_DELETED"));

        // 쓴 사람이 지운 글은 되살리지 않는다. 운영자는 자기가 가린 글만 되돌린다
        mockMvc.perform(put(url("/visibility")).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hidden":false}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_DELETED_BY_WRITER"));
        GuestbookEntry kept = entryRepository.findById(entry.getId()).orElseThrow();
        assertThat(kept.isDeleted()).isTrue();
        assertThat(kept.getDeletedAt()).isEqualTo(deletedAt);
        mockMvc.perform(get(ADMIN_GUESTBOOK).param("status", "DELETED").with(asAdmin()))
                .andExpect(jsonPath("$.data.items[0].deletedByWriter").value(true));
    }

    @Test
    @DisplayName("쓴 사람이 지운 뒤 가려도 지움 목록과 집계에 남고 보관 기한은 바뀌지 않는다")
    void writerDeletedThenHiddenKeepsDeletedClassification() throws Exception {
        LocalDateTime deletedAt = LocalDateTime.now().minusDays(1).withSecond(15).withNano(0);
        entry.delete(deletedAt);
        GuestbookEntry deleted = entryRepository.save(new GuestbookEntry(writer, "지운 글", false));
        deleted.delete(deletedAt);
        GuestbookEntry hidden = entryRepository.save(new GuestbookEntry(writer, "운영자가 가린 글", false));
        hidden.hideByAdmin();
        entryRepository.save(new GuestbookEntry(writer, "공개 글", false));

        mockMvc.perform(put(url("/visibility")).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hidden":true}
                                """))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        mockMvc.perform(get(ADMIN_GUESTBOOK).param("status", "DELETED").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(deleted.getId()))
                .andExpect(jsonPath("$.data.items[1].id").value(entry.getId()))
                .andExpect(jsonPath("$.data.items[1].deletedByWriter").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.statusCounts.all").value(4))
                .andExpect(jsonPath("$.data.statusCounts.deleted").value(2))
                .andExpect(jsonPath("$.data.statusCounts.hidden").value(1));
        mockMvc.perform(get(ADMIN_GUESTBOOK).param("status", "HIDDEN").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(hidden.getId()))
                .andExpect(jsonPath("$.data.items[0].deletedByWriter").value(false))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.statusCounts.deleted").value(2))
                .andExpect(jsonPath("$.data.statusCounts.hidden").value(1));
        mockMvc.perform(get(url("")).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entry.deletedByWriter").value(true))
                .andExpect(jsonPath("$.data.entry.hiddenByAdmin").value(true))
                .andExpect(jsonPath("$.data.deletedAt").value(deletedAt.toString()))
                .andExpect(jsonPath("$.data.retainedUntil").value(deletedAt.plusMonths(6).toString()));

        mockMvc.perform(put(url("/visibility")).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hidden":false}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_DELETED_BY_WRITER"));
        assertThat(entryRepository.findById(entry.getId()).orElseThrow().getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    @DisplayName("지운 시각이 없는 기존 작성자 삭제 글도 지움으로 분류한다")
    void writerDeletedWithoutTimestampKeepsDeletedClassification() throws Exception {
        ReflectionTestUtils.setField(entry, "deleted", true);
        em.flush();
        em.clear();

        mockMvc.perform(get(ADMIN_GUESTBOOK).param("status", "DELETED").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(entry.getId()))
                .andExpect(jsonPath("$.data.items[0].deletedByWriter").value(true))
                .andExpect(jsonPath("$.data.statusCounts.deleted").value(1))
                .andExpect(jsonPath("$.data.statusCounts.hidden").value(0));
    }

    @Test
    @DisplayName("원문을 파기한 글은 되살리거나 가릴 수 없다")
    void purgedEntryCannotChange() throws Exception {
        entry.delete(LocalDateTime.now().minusYears(1));
        ReflectionTestUtils.setField(entry, "contentPurgedAt", LocalDateTime.now());
        em.flush();

        mockMvc.perform(put(url("/visibility")).with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hidden":false}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_CONTENT_PURGED"));
    }

    @Test
    @DisplayName("검색어는 닉네임과 본문에서 찾는다")
    void searchesByKeyword() throws Exception {
        entryRepository.save(new GuestbookEntry(reporter, "다른 이야기", false));

        mockMvc.perform(get(ADMIN_GUESTBOOK).param("keyword", "인사").with(asAdmin()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].nickname").value("손님"));
        mockMvc.perform(get(ADMIN_GUESTBOOK).param("keyword", "신고자").with(asAdmin()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].content").value("다른 이야기"));
    }
}
