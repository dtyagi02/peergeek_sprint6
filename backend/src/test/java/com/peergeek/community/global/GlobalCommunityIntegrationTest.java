package com.peergeek.community.global;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.AbstractIntegrationTest;
import com.peergeek.audit.AuditAction;
import com.peergeek.audit.AuditLogEntity;
import com.peergeek.audit.AuditLogRepository;
import com.peergeek.community.global.dto.BlockCommunityUserRequest;
import com.peergeek.community.global.dto.CreateGlobalMessageRequest;
import com.peergeek.community.global.dto.DeleteGlobalMessageRequest;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserMode;
import com.peergeek.user.UserRepository;
import com.peergeek.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalCommunityIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository users;

	@Autowired
	private GlobalCommunityMessageRepository messageRepository;

	@Autowired
	private GlobalCommunityBlockRepository blockRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbc;

	private UserEntity student;
	private UserEntity instructor;
	private UserEntity admin;
	private UserEntity otherStudent;

	private UserEntity createUser(String email, String username, UserRole role, UserMode mode) {
		UserEntity user = new UserEntity(email, "hashedPassword", username, username, true);
		user.setRole(role);
		user.changeMode(mode);
		return users.save(user);
	}

	@BeforeEach
	void setUp() {
		jdbc.update("delete from global_community_blocks");
		jdbc.update("delete from global_community_messages");
		jdbc.update("delete from audit_logs");
		jdbc.update("delete from refresh_tokens");
		jdbc.update("delete from users");

		String uid = UUID.randomUUID().toString().substring(0, 8);
		student = createUser("student_" + uid + "@example.com", "Student_" + uid, UserRole.USER, UserMode.STUDENT);
		instructor = createUser("instructor_" + uid + "@example.com", "Instructor_" + uid, UserRole.USER, UserMode.INSTRUCTOR);
		admin = createUser("admin_" + uid + "@example.com", "Admin_" + uid, UserRole.ADMIN, UserMode.STUDENT);
		otherStudent = createUser("other_" + uid + "@example.com", "Other_" + uid, UserRole.USER, UserMode.STUDENT);
	}

	@Test
	@DisplayName("Users can post and retrieve messages with proper author details and roles")
	void postAndRetrieveMessages() throws Exception {
		String studentToken = jwtService.issue(student);
		String instructorToken = jwtService.issue(instructor);

		// Student posts a message
		mockMvc.perform(post("/api/v1/community/global/messages")
						.header("Authorization", "Bearer " + studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateGlobalMessageRequest("Hello from student!"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.content").value("Hello from student!"))
				.andExpect(jsonPath("$.authorId").value(student.getId()))
				.andExpect(jsonPath("$.authorMode").value("STUDENT"));

		// Instructor posts a message
		mockMvc.perform(post("/api/v1/community/global/messages")
						.header("Authorization", "Bearer " + instructorToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateGlobalMessageRequest("Welcome to PeerGeek Community!"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.content").value("Welcome to PeerGeek Community!"))
				.andExpect(jsonPath("$.authorId").value(instructor.getId()))
				.andExpect(jsonPath("$.authorMode").value("INSTRUCTOR"));

		// Fetch messages returns both
		mockMvc.perform(get("/api/v1/community/global/messages")
						.header("Authorization", "Bearer " + studentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].content").value("Hello from student!"))
				.andExpect(jsonPath("$[1].content").value("Welcome to PeerGeek Community!"));
	}

	@Test
	@DisplayName("Incremental polling returns only new messages after given ID")
	void incrementalPolling() throws Exception {
		String studentToken = jwtService.issue(student);

		// Post msg 1
		String res1 = mockMvc.perform(post("/api/v1/community/global/messages")
						.header("Authorization", "Bearer " + studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateGlobalMessageRequest("First"))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long msg1Id = objectMapper.readTree(res1).get("id").asLong();

		// Post msg 2
		mockMvc.perform(post("/api/v1/community/global/messages")
						.header("Authorization", "Bearer " + studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateGlobalMessageRequest("Second"))))
				.andExpect(status().isCreated());

		// Poll after msg1Id
		mockMvc.perform(get("/api/v1/community/global/messages")
						.param("afterId", msg1Id.toString())
						.header("Authorization", "Bearer " + studentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].content").value("Second"));
	}

	@Test
	@DisplayName("Message validation rejects empty and oversized messages")
	void messageValidation() throws Exception {
		String token = jwtService.issue(student);

		// Blank message
		mockMvc.perform(post("/api/v1/community/global/messages")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateGlobalMessageRequest("   "))))
				.andExpect(status().isBadRequest());

		// Exceed 2000 characters
		String longContent = "A".repeat(2001);
		mockMvc.perform(post("/api/v1/community/global/messages")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateGlobalMessageRequest(longContent))))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Regular user can delete their own message but not others; admin can delete any message")
	void messageDeletionPermissions() throws Exception {
		String studentToken = jwtService.issue(student);
		String otherToken = jwtService.issue(otherStudent);
		String adminToken = jwtService.issue(admin);

		// Student posts msg
		String res = mockMvc.perform(post("/api/v1/community/global/messages")
						.header("Authorization", "Bearer " + studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateGlobalMessageRequest("Student note"))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long msgId = objectMapper.readTree(res).get("id").asLong();

		// Other user tries to delete -> 403
		mockMvc.perform(delete("/api/v1/community/global/messages/" + msgId)
						.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isForbidden());

		// Admin deletes with reason -> 204 No Content
		mockMvc.perform(delete("/api/v1/community/global/messages/" + msgId)
						.header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new DeleteGlobalMessageRequest("Inappropriate content"))))
				.andExpect(status().isNoContent());

		// Normal feed no longer includes the deleted message
		mockMvc.perform(get("/api/v1/community/global/messages")
						.header("Authorization", "Bearer " + studentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));

		// Audit log created for deletion
		var logs = auditLogRepository.findAll();
		assertThat(logs).anyMatch(l -> AuditAction.COMMUNITY_MESSAGE_DELETED.equals(l.getAction())
				&& admin.getId().equals(l.getActorUserId()));
	}

	@Test
	@DisplayName("Admin can block user from community; blocked user cannot post but account remains enabled")
	void communityUserBlocking() throws Exception {
		String adminToken = jwtService.issue(admin);
		String studentToken = jwtService.issue(student);

		// Check status initially not blocked
		mockMvc.perform(get("/api/v1/community/global/status")
						.header("Authorization", "Bearer " + studentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blocked").value(false));

		// Admin blocks student from community
		mockMvc.perform(post("/api/v1/admin/community/users/" + student.getId() + "/block")
						.header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new BlockCommunityUserRequest("Spamming global chat"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(student.getId()))
				.andExpect(jsonPath("$.reason").value("Spamming global chat"));

		// Verify student's account is STILL enabled
		UserEntity reloadedStudent = users.findById(student.getId()).orElseThrow();
		assertThat(reloadedStudent.isEnabled()).isTrue();

		// Blocked student checks community status
		mockMvc.perform(get("/api/v1/community/global/status")
						.header("Authorization", "Bearer " + studentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blocked").value(true))
				.andExpect(jsonPath("$.reason").value("Spamming global chat"));

		// Blocked student attempts to post -> 403 Forbidden
		mockMvc.perform(post("/api/v1/community/global/messages")
						.header("Authorization", "Bearer " + studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateGlobalMessageRequest("Trying to bypass"))))
				.andExpect(status().isForbidden());

		// Admin unblocks student
		mockMvc.perform(post("/api/v1/admin/community/users/" + student.getId() + "/unblock")
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isNoContent());

		// Status reflects unblocked
		mockMvc.perform(get("/api/v1/community/global/status")
						.header("Authorization", "Bearer " + studentToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blocked").value(false));

		// Unblocked student can post again
		mockMvc.perform(post("/api/v1/community/global/messages")
						.header("Authorization", "Bearer " + studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateGlobalMessageRequest("I am unblocked!"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.content").value("I am unblocked!"));
	}

	@Test
	@DisplayName("Admin community monitoring endpoints require ADMIN role and expose deleted messages")
	void adminMonitoringEndpoints() throws Exception {
		String adminToken = jwtService.issue(admin);
		String studentToken = jwtService.issue(student);

		// Non-admin accessing admin endpoints -> 403 Forbidden
		mockMvc.perform(get("/api/v1/admin/community/messages")
						.header("Authorization", "Bearer " + studentToken))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/admin/community/users/" + otherStudent.getId() + "/block")
						.header("Authorization", "Bearer " + studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new BlockCommunityUserRequest("Illegal call"))))
				.andExpect(status().isForbidden());

		// Student posts message
		mockMvc.perform(post("/api/v1/community/global/messages")
						.header("Authorization", "Bearer " + studentToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateGlobalMessageRequest("Admin will see this"))))
				.andExpect(status().isCreated());

		// Admin views messages list
		mockMvc.perform(get("/api/v1/admin/community/messages")
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].content").value("Admin will see this"))
				.andExpect(jsonPath("$.content[0].deleted").value(false));
	}
}
