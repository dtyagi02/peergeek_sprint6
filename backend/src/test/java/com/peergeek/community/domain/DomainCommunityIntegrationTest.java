package com.peergeek.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.AbstractIntegrationTest;
import com.peergeek.audit.AuditAction;
import com.peergeek.audit.AuditLogRepository;
import com.peergeek.community.domain.dto.SendDomainMessageRequest;
import com.peergeek.community.global.GlobalCommunityBlockEntity;
import com.peergeek.community.global.GlobalCommunityBlockRepository;
import com.peergeek.course.CategoryEntity;
import com.peergeek.course.CategoryRepository;
import com.peergeek.notification.NotificationEntity;
import com.peergeek.notification.NotificationRepository;
import com.peergeek.notification.NotificationType;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import com.peergeek.user.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DomainCommunityIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private DomainCommunityRepository domainCommunityRepository;

	@Autowired
	private DomainCommunityMembershipRepository membershipRepository;

	@Autowired
	private DomainChatMessageRepository messageRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private GlobalCommunityBlockRepository blockRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private JwtService jwtService;

	private UserEntity alice;
	private UserEntity bob;
	private UserEntity admin;
	private String aliceToken;
	private String bobToken;
	private String adminToken;
	private CategoryEntity category;

	@BeforeEach
	void setUp() {
		blockRepository.deleteAll();
		auditLogRepository.deleteAll();
		notificationRepository.deleteAll();
		messageRepository.deleteAll();
		membershipRepository.deleteAll();

		String uid = UUID.randomUUID().toString().substring(0, 8);
		alice = userRepository.save(new UserEntity(
				"alice_" + uid + "@test.local",
				"hashed-pass",
				"Alice Domain",
				"alice_" + uid
		));

		bob = userRepository.save(new UserEntity(
				"bob_" + uid + "@test.local",
				"hashed-pass",
				"Bob Domain",
				"bob_" + uid
		));

		admin = new UserEntity(
				"admin_" + uid + "@test.local",
				"hashed-pass",
				"Admin Domain",
				"admin_" + uid
		);
		admin.setRole(UserRole.ADMIN);
		admin = userRepository.save(admin);

		aliceToken = "Bearer " + jwtService.issue(alice);
		bobToken = "Bearer " + jwtService.issue(bob);
		adminToken = "Bearer " + jwtService.issue(admin);

		category = categoryRepository.findAll().stream()
				.filter(c -> c.getSlug().equals("programming"))
				.findFirst()
				.orElseGet(() -> {
					List<CategoryEntity> all = categoryRepository.findAll();
					return all.get(0);
				});
	}

	@Test
	@DisplayName("Unauthenticated visitor can view domain community info")
	void getDomainCommunityPublic() throws Exception {
		mockMvc.perform(get("/api/v1/domains/{slug}/community", category.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value(category.getSlug()))
				.andExpect(jsonPath("$.name").value(category.getName()))
				.andExpect(jsonPath("$.isMember").value(false));
	}

	@Test
	@DisplayName("Joining domain community creates membership and generates SYSTEM_JOIN message")
	void joinCommunityGeneratesSystemMessage() throws Exception {
		// Alice joins
		mockMvc.perform(post("/api/v1/domains/{slug}/community/join", category.getSlug())
						.header("Authorization", aliceToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isMember").value(true))
				.andExpect(jsonPath("$.memberCount").value(1));

		// Check message stream contains SYSTEM_JOIN message
		mockMvc.perform(get("/api/v1/domains/{slug}/community/messages", category.getSlug())
						.header("Authorization", aliceToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].messageType").value("SYSTEM_JOIN"))
				.andExpect(jsonPath("$[0].content").value("Alice Domain joined " + category.getName() + "."));

		// Joining again is idempotent
		mockMvc.perform(post("/api/v1/domains/{slug}/community/join", category.getSlug())
						.header("Authorization", aliceToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.memberCount").value(1));

		// Still exactly 1 message
		mockMvc.perform(get("/api/v1/domains/{slug}/community/messages", category.getSlug())
						.header("Authorization", aliceToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	@DisplayName("Non-member sending message is rejected with 403 Forbidden")
	void nonMemberCannotSendMessage() throws Exception {
		SendDomainMessageRequest req = new SendDomainMessageRequest("Hello from non-member!");

		mockMvc.perform(post("/api/v1/domains/{slug}/community/messages", category.getSlug())
						.header("Authorization", bobToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Member can send message and mention other users generating notification")
	void memberCanSendMessageWithMentions() throws Exception {
		// Both join
		mockMvc.perform(post("/api/v1/domains/{slug}/community/join", category.getSlug())
						.header("Authorization", aliceToken))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/domains/{slug}/community/join", category.getSlug())
						.header("Authorization", bobToken))
				.andExpect(status().isOk());

		// Bob sends a message mentioning Alice
		SendDomainMessageRequest req = new SendDomainMessageRequest("Hey @" + alice.getUsername() + ", check out this algorithm!");

		mockMvc.perform(post("/api/v1/domains/{slug}/community/messages", category.getSlug())
						.header("Authorization", bobToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.content").value("Hey @" + alice.getUsername() + ", check out this algorithm!"))
				.andExpect(jsonPath("$.isSender").value(true));

		// Verify notification for Alice
		List<NotificationEntity> notifications = notificationRepository.findAll();
		assertThat(notifications).anyMatch(n ->
				n.getUserId().equals(alice.getId()) &&
						n.getType() == NotificationType.COMMUNITY_MENTION
		);
	}

	@Test
	@DisplayName("Member can leave community and member count decreases")
	void leaveCommunityDecrementsCount() throws Exception {
		// Alice joins
		mockMvc.perform(post("/api/v1/domains/{slug}/community/join", category.getSlug())
						.header("Authorization", aliceToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.memberCount").value(1));

		// Alice leaves
		mockMvc.perform(post("/api/v1/domains/{slug}/community/leave", category.getSlug())
						.header("Authorization", aliceToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isMember").value(false))
				.andExpect(jsonPath("$.memberCount").value(0));
	}

	@Test
	@DisplayName("Author can delete their own domain message")
	void authorCanDeleteOwnMessage() throws Exception {
		// Alice joins and posts a message
		mockMvc.perform(post("/api/v1/domains/{slug}/community/join", category.getSlug())
						.header("Authorization", aliceToken))
				.andExpect(status().isOk());

		String res = mockMvc.perform(post("/api/v1/domains/{slug}/community/messages", category.getSlug())
						.header("Authorization", aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new SendDomainMessageRequest("Hello from Alice!"))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		Long messageId = objectMapper.readTree(res).get("id").asLong();

		// Alice deletes her own message
		mockMvc.perform(delete("/api/v1/domains/{slug}/community/messages/{messageId}", category.getSlug(), messageId)
						.header("Authorization", aliceToken))
				.andExpect(status().isNoContent());

		// Verify deleted from repository
		assertThat(messageRepository.findById(messageId)).isEmpty();

		// Verify audit log recorded
		var logs = auditLogRepository.findAll();
		assertThat(logs).anyMatch(l -> AuditAction.COMMUNITY_MESSAGE_DELETED.equals(l.getAction())
				&& alice.getId().equals(l.getActorUserId()));
	}

	@Test
	@DisplayName("Non-author regular user cannot delete another user's domain message")
	void nonAuthorCannotDeleteDomainMessage() throws Exception {
		// Alice joins and posts
		mockMvc.perform(post("/api/v1/domains/{slug}/community/join", category.getSlug())
						.header("Authorization", aliceToken))
				.andExpect(status().isOk());

		String res = mockMvc.perform(post("/api/v1/domains/{slug}/community/messages", category.getSlug())
						.header("Authorization", aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new SendDomainMessageRequest("Alice's private message"))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		Long messageId = objectMapper.readTree(res).get("id").asLong();

		// Bob joins
		mockMvc.perform(post("/api/v1/domains/{slug}/community/join", category.getSlug())
						.header("Authorization", bobToken))
				.andExpect(status().isOk());

		// Bob attempts to delete Alice's message -> 403 Forbidden
		mockMvc.perform(delete("/api/v1/domains/{slug}/community/messages/{messageId}", category.getSlug(), messageId)
						.header("Authorization", bobToken))
				.andExpect(status().isForbidden());

		// Message still exists
		assertThat(messageRepository.findById(messageId)).isPresent();
	}

	@Test
	@DisplayName("Admin can delete any user's domain message with audit log")
	void adminCanDeleteAnyDomainMessage() throws Exception {
		// Alice joins and posts
		mockMvc.perform(post("/api/v1/domains/{slug}/community/join", category.getSlug())
						.header("Authorization", aliceToken))
				.andExpect(status().isOk());

		String res = mockMvc.perform(post("/api/v1/domains/{slug}/community/messages", category.getSlug())
						.header("Authorization", aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new SendDomainMessageRequest("Spam message"))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		Long messageId = objectMapper.readTree(res).get("id").asLong();

		// Admin deletes message -> 204 No Content
		mockMvc.perform(delete("/api/v1/domains/{slug}/community/messages/{messageId}", category.getSlug(), messageId)
						.header("Authorization", adminToken))
				.andExpect(status().isNoContent());

		// Message is deleted
		assertThat(messageRepository.findById(messageId)).isEmpty();

		// Verify admin audit log
		var logs = auditLogRepository.findAll();
		assertThat(logs).anyMatch(l -> AuditAction.COMMUNITY_MESSAGE_DELETED.equals(l.getAction())
				&& admin.getId().equals(l.getActorUserId()));
	}

	@Test
	@DisplayName("Blocked user is forbidden from sending domain messages")
	void blockedUserCannotSendDomainMessage() throws Exception {
		// Alice is blocked from community
		blockRepository.save(new GlobalCommunityBlockEntity(alice, admin, "Spamming discussions"));

		// Alice joins community
		mockMvc.perform(post("/api/v1/domains/{slug}/community/join", category.getSlug())
						.header("Authorization", aliceToken))
				.andExpect(status().isOk());

		// Alice tries to post message -> 403 Forbidden
		mockMvc.perform(post("/api/v1/domains/{slug}/community/messages", category.getSlug())
						.header("Authorization", aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new SendDomainMessageRequest("Attempting to post"))))
				.andExpect(status().isForbidden());
	}
}
