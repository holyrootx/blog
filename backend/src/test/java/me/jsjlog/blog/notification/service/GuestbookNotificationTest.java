package me.jsjlog.blog.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.guestbook.domain.GuestbookEntry;
import me.jsjlog.blog.guestbook.repository.GuestbookEntryRepository;
import me.jsjlog.blog.member.domain.AuthProvider;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.notification.domain.Notification;
import me.jsjlog.blog.notification.domain.NotificationType;
import me.jsjlog.blog.notification.dto.NotificationResponse;
import me.jsjlog.blog.notification.repository.NotificationRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.domain.Comment;
import me.jsjlog.blog.post.domain.Post;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.post.repository.CommentRepository;
import me.jsjlog.blog.post.repository.PostRepository;
import me.jsjlog.blog.support.MySqlTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종 하나에 댓글 알림과 방명록 알림이 같이 뜬다.
 *
 * <p>알림 조회가 댓글을 안 조인으로 붙이면 방명록 알림이 통째로 빠진다. 반대로 방명록을 붙이다
 * 댓글 알림 조건이 풀리면 비공개 글의 알림이 새어 나간다. 둘 다 여기서 본다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuestbookNotificationTest {

	@DynamicPropertySource
	static void isolatedDatabase(DynamicPropertyRegistry registry) {
		MySqlTestDatabase.configure(registry, "guestbook_notification");
	}

	@Autowired private MockMvc mockMvc;
	@Autowired private NotificationService notifications;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private GuestbookEntryRepository entries;
	@Autowired private MemberRepository members;
	@Autowired private CategoryRepository categories;
	@Autowired private PostRepository posts;
	@Autowired private CommentRepository comments;
	@Autowired private EntityManager em;

	private Member admin;
	private Member guest;
	private GuestbookEntry entry;
	private Comment comment;

	@BeforeEach
	void setUp() {
		admin = members.save(Member.ofLocalAdmin("guest-noti-admin", "encoded-password", "운영자"));
		guest = members.save(Member.ofSocial(AuthProvider.KAKAO, "guest-noti-guest", "손님", null, null));

		Category category = categories.save(new Category("방명록 알림", 970L));
		Post post = new Post("알림 글", "요약", "본문", category, null, admin);
		post.publish(LocalDateTime.now().minusDays(1));
		post = posts.save(post);

		comment = comments.save(new Comment(post, null, guest, "글에 단 댓글"));
		entry = entries.save(new GuestbookEntry(guest, "방명록 인사", true));
	}

	@Test
	@DisplayName("운영자의 종에는 댓글 알림과 방명록 알림이 최신순으로 같이 뜬다")
	void showsCommentAndGuestbookTogether() throws Exception {
		notifications.notifyCommentCreated(comment);
		notifications.notifyGuestbookWritten(entry);
		em.flush();
		em.clear();

		assertThat(notifications.findMine(admin.getId()))
				.extracting(NotificationResponse::type)
				.containsExactly(NotificationType.GUESTBOOK_WRITTEN, NotificationType.POST_COMMENT);
		assertThat(notifications.countUnread(admin.getId())).isEqualTo(2);

		mockMvc.perform(get("/api/v1/auth/me/notifications").with(user(MemberPrincipal.ofLocal(admin))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].type").value("GUESTBOOK_WRITTEN"))
				.andExpect(jsonPath("$.data[0].guestbookEntryId").value(entry.getId()))
				.andExpect(jsonPath("$.data[0].actorNickname").value("손님"))
				.andExpect(jsonPath("$.data[0].preview").value("방명록 인사"))
				.andExpect(jsonPath("$.data[0].postId").doesNotExist())
				.andExpect(jsonPath("$.data[1].type").value("POST_COMMENT"))
				.andExpect(jsonPath("$.data[1].commentId").value(comment.getId()));

		notifications.markAllRead(admin.getId());
		assertThat(notifications.countUnread(admin.getId())).isZero();
	}

	@Test
	@DisplayName("지운 방명록의 새 글 알림은 빠지고, 신고 알림은 남는다")
	void deletedEntryKeepsOnlyReportWork() {
		notifications.notifyGuestbookWritten(entry);
		notifications.notifyGuestbookReported(entry);
		entry.delete(LocalDateTime.now());
		em.flush();
		em.clear();

		assertThat(notifications.findMine(admin.getId()))
				.extracting(NotificationResponse::type)
				.containsExactly(NotificationType.GUESTBOOK_REPORTED);
		assertThat(notifications.findMine(admin.getId()))
				.extracting(NotificationResponse::preview)
				.containsExactly("방명록 인사");
		assertThat(notifications.countUnread(admin.getId())).isEqualTo(1);
	}

	@Test
	@DisplayName("운영자가 답하면 쓴 사람의 종에 답한 운영자와 답글이 뜬다")
	void writerSeesReply() throws Exception {
		entry.reply(admin, "와 주셔서 고마워요", LocalDateTime.now());
		notifications.notifyGuestbookReplied(entry);
		em.flush();
		em.clear();

		mockMvc.perform(get("/api/v1/auth/me/notifications").with(user(MemberPrincipal.ofSocial(guest))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].type").value("GUESTBOOK_REPLIED"))
				.andExpect(jsonPath("$.data[0].actorNickname").value("운영자"))
				.andExpect(jsonPath("$.data[0].preview").value("와 주셔서 고마워요"))
				.andExpect(jsonPath("$.data[0].guestbookEntryId").value(entry.getId()));
		assertThat(notifications.countUnread(guest.getId())).isEqualTo(1);
		assertThat(notifications.findMine(admin.getId())).isEmpty();
	}

	@Test
	@DisplayName("답글을 지우거나 글이 안 보이게 되면 답글 알림도 빠진다")
	void replyNotificationFollowsEntry() {
		entry.reply(admin, "답장", LocalDateTime.now());
		notifications.notifyGuestbookReplied(entry);
		em.flush();

		entry.removeReply();
		em.flush();
		assertThat(notifications.countUnread(guest.getId())).isZero();

		entry.reply(admin, "다시 답장", LocalDateTime.now());
		entry.hideByAdmin();
		em.flush();
		em.clear();
		assertThat(notifications.findMine(guest.getId())).isEmpty();
	}

	@Test
	@DisplayName("운영자가 자기 글에 답하면 알리지 않는다")
	void adminOwnEntryReplyIsSilent() {
		GuestbookEntry own = entries.save(new GuestbookEntry(admin, "운영자가 남긴 글", false));
		own.reply(admin, "덧붙임", LocalDateTime.now());
		notifications.notifyGuestbookReplied(own);
		em.flush();

		assertThat(notificationRepository.count()).isZero();
	}

	@Test
	@DisplayName("가림 알림에는 원문이 없고, 관리자만 본문과 답글을 본다. 다시 공개하면 알림은 빠진다")
	void hiddenNotificationFollowsVisibility() throws Exception {
		entry.reply(admin, "관리자 답글 원문", LocalDateTime.now());
		entry.hideByAdmin();
		notifications.notifyGuestbookHidden(entry, admin);
		em.flush();
		em.clear();

		mockMvc.perform(get("/api/v1/auth/me/notifications").with(user(MemberPrincipal.ofSocial(guest))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].type").value("GUESTBOOK_HIDDEN"))
				.andExpect(jsonPath("$.data[0].preview").value(""))
				.andExpect(jsonPath("$.data[0].guestbookEntryId").value(entry.getId()));
		assertThat(notifications.countUnread(guest.getId())).isEqualTo(1);

		mockMvc.perform(get("/api/v1/blog/guestbook").with(user(MemberPrincipal.ofSocial(guest))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].hiddenByAdmin").value(true))
				.andExpect(jsonPath("$.data.items[0].content").doesNotExist())
				.andExpect(jsonPath("$.data.items[0].reply").doesNotExist());
		mockMvc.perform(get("/api/v1/admin/blog/guestbook/" + entry.getId())
						.with(user(MemberPrincipal.ofLocal(admin))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entry.content").value("방명록 인사"))
				.andExpect(jsonPath("$.data.entry.replyContent").value("관리자 답글 원문"));
		assertThat(notifications.findMine(admin.getId())).isEmpty();

		GuestbookEntry restored = entries.findById(entry.getId()).orElseThrow();
		restored.restore();
		em.flush();
		assertThat(notifications.countUnread(guest.getId())).isZero();
	}

	@Test
	@DisplayName("운영자가 자기 글을 가리면 알리지 않는다")
	void adminOwnEntryHideIsSilent() {
		GuestbookEntry own = entries.save(new GuestbookEntry(admin, "운영자가 남긴 글", false));
		own.hideByAdmin();
		notifications.notifyGuestbookHidden(own, admin);
		em.flush();

		assertThat(notificationRepository.count()).isZero();
	}

	@Test
	@DisplayName("방명록 알림은 운영자가 아닌 사람에게 잘못 저장돼도 보이지 않는다")
	void guestbookNotificationIsAdminOnly() {
		notificationRepository.save(new Notification(guest, NotificationType.GUESTBOOK_WRITTEN, entry));
		em.flush();
		em.clear();

		assertThat(notifications.findMine(guest.getId())).isEmpty();
		assertThat(notifications.countUnread(guest.getId())).isZero();
	}
}
