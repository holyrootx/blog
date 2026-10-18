package me.jsjlog.blog.guestbook.controller;

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
import java.util.List;

import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.guestbook.domain.GuestbookEntry;
import me.jsjlog.blog.guestbook.repository.GuestbookEntryRepository;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 방명록.
 *
 * <p>읽기는 누구나, 쓰기는 회원만. 비밀글은 응답에서부터 비어 있어야 한다 — 화면에서만 가리면
 * 누구나 개발자 도구로 읽는다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuestbookApiTest {

    private static final String GUESTBOOK = "/api/v1/blog/guestbook";

    @Autowired private MockMvc mockMvc;
    @Autowired private MemberRepository memberRepository;
    @Autowired private GuestbookEntryRepository entryRepository;
    @Autowired private NotificationRepository notificationRepository;

    private Member writer;
    private Member reader;
    private Member admin;

    @BeforeEach
    void setUp() {
        writer = member("guestbook-writer", "손님");
        reader = member("guestbook-reader", "다른손님");
        admin = memberRepository.save(Member.ofLocalAdmin("guestbook-admin", "encoded-password", "운영자"));
    }

    private Member member(String providerId, String nickname) {
        return memberRepository.save(Member.ofSocial(AuthProvider.KAKAO, providerId, nickname, null, null));
    }

    private RequestPostProcessor as(Member member) {
        return user(member.isAdmin() ? MemberPrincipal.ofLocal(member) : MemberPrincipal.ofSocial(member));
    }

    private GuestbookEntry entry(Member owner, String content, boolean secret) {
        return entryRepository.save(new GuestbookEntry(owner, content, secret));
    }

    private List<Notification> notificationsOf(Member recipient) {
        return notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipient().getId().equals(recipient.getId()))
                .toList();
    }

    private String body(String content, boolean secret) {
        return """
                {"content":%s,"secret":%s}
                """.formatted(content == null ? "null" : "\"" + content + "\"", secret);
    }

    @Test
    @DisplayName("로그인하지 않아도 방명록을 읽을 수 있다")
    void anonymousCanRead() throws Exception {
        entry(writer, "안녕하세요", false);

        mockMvc.perform(get(GUESTBOOK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].content").value("안녕하세요"))
                .andExpect(jsonPath("$.data.items[0].nickname").value("손님"))
                .andExpect(jsonPath("$.data.items[0].mine").value(false))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.maxLength").value(500));
    }

    @Test
    @DisplayName("비밀글은 쓴 사람과 운영자만 본문을 받는다 — 나머지에게는 응답에서부터 비어 있다")
    void secretEntryIsMaskedInResponse() throws Exception {
        GuestbookEntry secret = entry(writer, "운영자님께만", true);
        secret.reply(admin, "답장입니다", LocalDateTime.now());

        mockMvc.perform(get(GUESTBOOK))
                .andExpect(jsonPath("$.data.items[0].secret").value(true))
                .andExpect(jsonPath("$.data.items[0].readable").value(false))
                .andExpect(jsonPath("$.data.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].reply").doesNotExist());

        mockMvc.perform(get(GUESTBOOK).with(as(reader)))
                .andExpect(jsonPath("$.data.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].reply").doesNotExist());

        mockMvc.perform(get(GUESTBOOK).with(as(writer)))
                .andExpect(jsonPath("$.data.items[0].content").value("운영자님께만"))
                .andExpect(jsonPath("$.data.items[0].mine").value(true))
                .andExpect(jsonPath("$.data.items[0].reply.content").value("답장입니다"));

        mockMvc.perform(get(GUESTBOOK).with(as(admin)))
                .andExpect(jsonPath("$.data.items[0].content").value("운영자님께만"))
                .andExpect(jsonPath("$.data.items[0].reply.nickname").value("운영자"));
    }

    @Test
    @DisplayName("글 번호를 주면 그 글이 있는 쪽을 준다 — 알림으로 찾아온 오래된 글")
    void findsPageOfEntry() throws Exception {
        GuestbookEntry oldest = entry(writer, "가장 오래된 글", false);
        for (int index = 0; index < 12; index++) {
            entry(reader, "새 글 " + index, false);
        }
        GuestbookEntry hidden = entry(reader, "가려진 글", false);
        hidden.hideByAdmin();

        mockMvc.perform(get(GUESTBOOK).param("size", "5").param("entry", String.valueOf(oldest.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.items[-1].id").value(oldest.getId()));

        // 볼 수 없는 글이면 첫 쪽을 준다. 가린 글이 있다는 것을 쪽 번호로 알려 주지 않는다
        mockMvc.perform(get(GUESTBOOK).param("size", "5").param("entry", String.valueOf(hidden.getId())))
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    @DisplayName("한 번에 50개보다 많이 달라고 하면 거절한다")
    void rejectsOversizedPage() throws Exception {
        mockMvc.perform(get(GUESTBOOK).param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_SIZE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("회원은 방명록을 남기고, 운영자에게 알림이 간다")
    void memberWritesAndAdminIsNotified() throws Exception {
        mockMvc.perform(post(GUESTBOOK).with(as(writer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  반가워요  ", true)))
                .andExpect(status().isOk());

        GuestbookEntry saved = entryRepository.findAll().getFirst();
        assertThat(saved.getContent()).isEqualTo("반가워요");
        assertThat(saved.isSecret()).isTrue();
        // 다른 테스트가 남긴 운영자가 있을 수 있어 이 운영자에게 간 것만 센다
        assertThat(notificationsOf(admin))
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getType()).isEqualTo(NotificationType.GUESTBOOK_WRITTEN);
                    assertThat(notification.getGuestbookEntry().getId()).isEqualTo(saved.getId());
                });
    }

    @Test
    @DisplayName("운영자가 직접 남긴 글은 운영자에게 알리지 않는다")
    void adminOwnEntryDoesNotNotifyAdmin() throws Exception {
        mockMvc.perform(post(GUESTBOOK).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("공지합니다", false)))
                .andExpect(status().isOk());

        assertThat(notificationsOf(admin)).isEmpty();
    }

    @Test
    @DisplayName("로그인하지 않으면 남길 수 없다")
    void anonymousCannotWrite() throws Exception {
        mockMvc.perform(post(GUESTBOOK).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("익명", false)))
                .andExpect(status().isUnauthorized());

        assertThat(entryRepository.count()).isZero();
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 회원이라도 막는다")
    void rejectsWithoutCsrf() throws Exception {
        mockMvc.perform(post(GUESTBOOK).with(as(writer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("토큰 없음", false)))
                .andExpect(status().isForbidden());

        assertThat(entryRepository.count()).isZero();
    }

    @Test
    @DisplayName("빈 글과 500자를 넘는 글은 받지 않는다")
    void validatesContent() throws Exception {
        mockMvc.perform(post(GUESTBOOK).with(as(writer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_CONTENT_REQUIRED"));

        mockMvc.perform(post(GUESTBOOK).with(as(writer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("가".repeat(501), false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_CONTENT_TOO_LONG"));

        mockMvc.perform(post(GUESTBOOK).with(as(writer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("가".repeat(500), false)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("1분에 세 번까지만 남길 수 있다 — 도배 방지")
    void limitsWritesPerMinute() throws Exception {
        for (int index = 1; index <= 3; index++) {
            mockMvc.perform(post(GUESTBOOK).with(as(reader)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("인사 " + index, false)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post(GUESTBOOK).with(as(reader)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("네 번째", false)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_RATE_LIMITED"));

        assertThat(entryRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("내 글은 고칠 수 있고 수정 표시가 붙는다. 비밀글 여부만 바꾸면 붙지 않는다")
    void ownerUpdates() throws Exception {
        GuestbookEntry mine = entry(writer, "처음 글", false);

        mockMvc.perform(put(GUESTBOOK + "/" + mine.getId()).with(as(writer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("처음 글", true)))
                .andExpect(status().isOk());
        assertThat(entryRepository.findById(mine.getId()).orElseThrow().isEdited()).isFalse();

        mockMvc.perform(put(GUESTBOOK + "/" + mine.getId()).with(as(writer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("고친 글", true)))
                .andExpect(status().isOk());

        GuestbookEntry updated = entryRepository.findById(mine.getId()).orElseThrow();
        assertThat(updated.getContent()).isEqualTo("고친 글");
        assertThat(updated.isSecret()).isTrue();
        assertThat(updated.isEdited()).isTrue();
    }

    @Test
    @DisplayName("남의 글은 고치거나 지울 수 없다. 남의 비밀글은 있는지도 알려 주지 않는다")
    void othersCannotChange() throws Exception {
        GuestbookEntry open = entry(writer, "공개 글", false);
        GuestbookEntry secret = entry(writer, "비밀 글", true);

        mockMvc.perform(put(GUESTBOOK + "/" + open.getId()).with(as(reader)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("바꿔치기", false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_NOT_MINE"));

        mockMvc.perform(delete(GUESTBOOK + "/" + secret.getId()).with(as(reader)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_NOT_FOUND"));

        // 운영자도 남의 글을 고치지는 못한다. 운영자에게는 가리기가 있다
        mockMvc.perform(put(GUESTBOOK + "/" + open.getId()).with(as(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("운영자 수정", false)))
                .andExpect(status().isForbidden());

        assertThat(entryRepository.findById(open.getId()).orElseThrow().getContent()).isEqualTo("공개 글");
        assertThat(entryRepository.findById(secret.getId()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("지운 글은 목록에서 빠진다")
    void deletedEntryLeavesList() throws Exception {
        GuestbookEntry mine = entry(writer, "지울 글", false);

        mockMvc.perform(delete(GUESTBOOK + "/" + mine.getId()).with(as(writer)).with(csrf()))
                .andExpect(status().isOk());

        assertThat(entryRepository.findById(mine.getId()).orElseThrow().getDeletedAt()).isNotNull();
        mockMvc.perform(get(GUESTBOOK).with(as(writer)))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("운영자가 가린 글은 쓴 사람에게만 남고, 고칠 수 없다")
    void hiddenEntryStaysOnlyForWriter() throws Exception {
        GuestbookEntry hidden = entry(writer, "가려질 글", false);
        hidden.hideByAdmin();

        mockMvc.perform(get(GUESTBOOK).with(as(reader)))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mockMvc.perform(get(GUESTBOOK))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        mockMvc.perform(get(GUESTBOOK).with(as(writer)))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].hiddenByAdmin").value(true));

        mockMvc.perform(put(GUESTBOOK + "/" + hidden.getId()).with(as(writer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("되살리기", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GUESTBOOK_HIDDEN_BY_ADMIN"));
    }

    @Test
    @DisplayName("정지된 회원은 남길 수 없다")
    void suspendedMemberCannotWrite() throws Exception {
        MemberPrincipal principal = MemberPrincipal.ofSocial(writer);
        writer.suspend(LocalDateTime.now());

        mockMvc.perform(post(GUESTBOOK).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("정지 중", false)))
                .andExpect(status().is4xxClientError());

        assertThat(entryRepository.count()).isZero();
    }
}
