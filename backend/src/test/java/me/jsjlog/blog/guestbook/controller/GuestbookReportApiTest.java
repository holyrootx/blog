package me.jsjlog.blog.guestbook.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방명록 신고. 댓글 신고와 같은 규칙이다 — 회원만, 남의 글만, 한 번만. 저절로 가리지 않고 운영자에게 알린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuestbookReportApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MemberRepository memberRepository;
    @Autowired private GuestbookEntryRepository entryRepository;
    @Autowired private GuestbookReportRepository reportRepository;
    @Autowired private NotificationRepository notificationRepository;

    private Member writer;
    private Member reader;
    private Member otherReader;
    private Member admin;
    private GuestbookEntry entry;

    @BeforeEach
    void setUp() {
        writer = member("report-guest-writer", "쓴사람");
        reader = member("report-guest-reader", "신고자");
        otherReader = member("report-guest-other", "또다른신고자");
        admin = memberRepository.save(Member.ofLocalAdmin("report-guest-admin", "encoded-password", "운영자"));
        entry = entryRepository.save(new GuestbookEntry(writer, "광고입니다", false));
    }

    private Member member(String providerId, String nickname) {
        return memberRepository.save(Member.ofSocial(AuthProvider.GOOGLE, providerId, nickname, null, null));
    }

    private List<Notification> notificationsOf(Member recipient) {
        return notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipient().getId().equals(recipient.getId()))
                .toList();
    }

    private ResultActions report(Member member, GuestbookEntry target) throws Exception {
        return mockMvc.perform(post("/api/v1/blog/guestbook/" + target.getId() + "/reports")
                .with(user(MemberPrincipal.ofSocial(member)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason":"SPAM","detail":"  광고  "}
                        """));
    }

    @Test
    @DisplayName("남의 글을 신고하면 신고 당시 본문이 남고 운영자에게 알림이 간다")
    void reportsAndNotifies() throws Exception {
        report(reader, entry).andExpect(status().isOk());

        GuestbookReport saved = reportRepository.findAll().getFirst();
        assertThat(saved.getReportedContent()).isEqualTo("광고입니다");
        assertThat(saved.getDetail()).isEqualTo("광고");
        assertThat(saved.getHandledAt()).isNull();
        // 다른 테스트가 남긴 운영자가 있을 수 있어 이 운영자에게 간 것만 센다
        assertThat(notificationsOf(admin))
                .singleElement()
                .satisfies(notification ->
                        assertThat(notification.getType()).isEqualTo(NotificationType.GUESTBOOK_REPORTED));

        // 목록에는 "이미 신고함" 으로 남는다. 신고 수는 보이지 않는다
        mockMvc.perform(get("/api/v1/blog/guestbook").with(user(MemberPrincipal.ofSocial(reader))))
                .andExpect(jsonPath("$.data.items[0].reportedByMe").value(true))
                .andExpect(jsonPath("$.data.items[0].reportCount").doesNotExist());
    }

    @Test
    @DisplayName("안 본 신고 알림이 있으면 신고가 더 와도 알림을 늘리지 않는다")
    void doesNotFloodAdmin() throws Exception {
        report(reader, entry).andExpect(status().isOk());
        report(otherReader, entry).andExpect(status().isOk());

        assertThat(reportRepository.count()).isEqualTo(2);
        assertThat(notificationsOf(admin)).hasSize(1);
    }

    @Test
    @DisplayName("로그인하지 않으면 신고할 수 없다")
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/blog/guestbook/" + entry.getId() + "/reports")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"SPAM"}
                                """))
                .andExpect(status().isUnauthorized());

        assertThat(reportRepository.count()).isZero();
    }

    @Test
    @DisplayName("내 글은 신고할 수 없고, 같은 글을 두 번 신고할 수 없다")
    void rejectsSelfAndDuplicate() throws Exception {
        report(writer, entry)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_REPORT_SELF"));

        report(reader, entry).andExpect(status().isOk());
        report(reader, entry)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_REPORT_DUPLICATED"));

        assertThat(reportRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("읽을 수 없는 남의 비밀글은 신고할 수 없다 — 번호로 있는지 확인하는 길이 된다")
    void cannotReportUnreadableSecret() throws Exception {
        GuestbookEntry secret = entryRepository.save(new GuestbookEntry(writer, "비밀", true));

        report(reader, secret)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_NOT_FOUND"));
    }

    @Test
    @DisplayName("사유가 없으면 받지 않는다")
    void requiresReason() throws Exception {
        mockMvc.perform(post("/api/v1/blog/guestbook/" + entry.getId() + "/reports")
                        .with(user(MemberPrincipal.ofSocial(reader)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_REPORT_REASON_REQUIRED"));
    }
}
