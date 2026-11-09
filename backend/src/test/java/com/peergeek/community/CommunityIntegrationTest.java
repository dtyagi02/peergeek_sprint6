package com.peergeek.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.AbstractIntegrationTest;
import com.peergeek.community.dto.CreateCommentRequest;
import com.peergeek.community.dto.CreatePostRequest;
import com.peergeek.notification.NotificationRepository;
import com.peergeek.notification.NotificationType;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import com.peergeek.user.UserRole;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CommunityPostRepository postRepository;

	@Autowired
	private CommunityCommentRepository commentRepository;

	@Autowired
	private CommunityMentionRepository mentionRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private JwtService jwtService;

	private UserEntity alice;
	private UserEntity bob;
	private String aliceToken;
	private String bobToken;

	@BeforeEach
	void setUp() {
		String uid = UUID.randomUUID().toString().substring(0, 8);
		alice = userRepository.save(new UserEntity("alice_" + uid + "@example.com", "hash", "Alice Smith", "alice_" + uid));
		bob = userRepository.save(new UserEntity("bob_" + uid + "@example.com", "hash", "Bob Jones", "bob_" + uid));

		aliceToken = "Bearer " + jwtService.issue(alice);
		bobToken = "Bearer " + jwtService.issue(bob);
	}

	@Test
	@DisplayName("1. Authenticated user can create post, and anonymous user cannot")
	void createPost() throws Exception {
		// Anonymous cannot create post
		mockMvc.perform(post("/api/v1/community/posts")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreatePostRequest("Hello world!"))))
				.andExpect(status().isUnauthorized());

		// Alice creates post
		MvcResult result = mockMvc.perform(post("/api/v1/community/posts")
						.header("Authorization", aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreatePostRequest("Hello from Alice!"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.content").value("Hello from Alice!"))
				.andExpect(jsonPath("$.author.username").value(alice.getUsername()))
				.andExpect(jsonPath("$.isAuthor").value(true))
				.andReturn();

		// Public get posts contains Alice's post
		mockMvc.perform(get("/api/v1/community/posts"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].content").value("Hello from Alice!"));
	}

	@Test
	@DisplayName("2. Bob can comment on Alice's post, and Alice can reply to Bob's comment")
	void commentAndReplyFlow() throws Exception {
		// Alice creates post
		String postResult = mockMvc.perform(post("/api/v1/community/posts")
						.header("Authorization", aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreatePostRequest("Which Spring Boot pattern do you prefer?"))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long postId = objectMapper.readTree(postResult).get("id").asLong();

		// Bob comments on Alice's post
		String commentResult = mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
						.header("Authorization", bobToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateCommentRequest("I recommend package-by-feature!", null))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.postId").value(postId))
				.andExpect(jsonPath("$.author.username").value(bob.getUsername()))
				.andReturn().getResponse().getContentAsString();
		Long commentId = objectMapper.readTree(commentResult).get("id").asLong();

		// Alice replies to Bob's comment
		mockMvc.perform(post("/api/v1/community/posts/" + postId + "/comments")
						.header("Authorization", aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateCommentRequest("Great suggestion @"+ bob.getUsername() +"! Agreed.", commentId))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.parentCommentId").value(commentId))
				.andExpect(jsonPath("$.author.username").value(alice.getUsername()));

		// Get comments returns Bob's comment with Alice's reply nested
		mockMvc.perform(get("/api/v1/community/posts/" + postId + "/comments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(commentId))
				.andExpect(jsonPath("$[0].content").value("I recommend package-by-feature!"))
				.andExpect(jsonPath("$[0].replies[0].parentCommentId").value(commentId))
				.andExpect(jsonPath("$[0].replies[0].content").value("Great suggestion @" + bob.getUsername() + "! Agreed."));
	}

	@Test
	@DisplayName("3. Replying to a comment belonging to another post is rejected")
	void rejectCrossPostReply() throws Exception {
		// Post 1
		String p1 = mockMvc.perform(post("/api/v1/community/posts")
						.header("Authorization", aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreatePostRequest("Post 1"))))
				.andReturn().getResponse().getContentAsString();
		Long post1Id = objectMapper.readTree(p1).get("id").asLong();

		// Post 2
		String p2 = mockMvc.perform(post("/api/v1/community/posts")
						.header("Authorization", aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreatePostRequest("Post 2"))))
				.andReturn().getResponse().getContentAsString();
		Long post2Id = objectMapper.readTree(p2).get("id").asLong();

		// Comment on Post 1
		String c1 = mockMvc.perform(post("/api/v1/community/posts/" + post1Id + "/comments")
						.header("Authorization", bobToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateCommentRequest("Comment on Post 1", null))))
				.andReturn().getResponse().getContentAsString();
		Long comment1Id = objectMapper.readTree(c1).get("id").asLong();

		// Attempt to reply on Post 2 referencing Comment 1 belonging to Post 1
		mockMvc.perform(post("/api/v1/community/posts/" + post2Id + "/comments")
						.header("Authorization", aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreateCommentRequest("Invalid reply", comment1Id))))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("4. User mentions create mention records and trigger notifications for mentioned user")
	void mentionsAndNotifications() throws Exception {
		// Alice posts mentioning Bob: "@bob_xyz what do you think?"
		String postResult = mockMvc.perform(post("/api/v1/community/posts")
						.header("Authorization", aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreatePostRequest("Hey @" + bob.getUsername() + ", checkout this idea!"))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long postId = objectMapper.readTree(postResult).get("id").asLong();

		// Verify mention record saved in DB
		boolean mentionExists = mentionRepository.existsByPostIdAndCommentIdAndMentionedUserId(postId, null, bob.getId());
		assertThat(mentionExists).isTrue();

		// Verify notification created for Bob
		boolean notificationExists = notificationRepository.findAll().stream()
				.anyMatch(n -> n.getUserId().equals(bob.getId())
						&& n.getType() == NotificationType.COMMUNITY_MENTION
						&& n.getResourceId().equals(postId));
		assertThat(notificationExists).isTrue();
	}

	@Test
	@DisplayName("5. Mention autocomplete returns real users")
	void mentionAutocomplete() throws Exception {
		mockMvc.perform(get("/api/v1/community/users/mention-suggestions")
						.header("Authorization", aliceToken)
						.param("q", bob.getUsername().substring(0, 3)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.username == '" + bob.getUsername() + "')]").exists())
				.andExpect(jsonPath("$[0].displayName").exists());
	}

	@Test
	@DisplayName("6. Author or Admin can delete post; unauthorized user gets 403 Forbidden")
	void deletePostPermissions() throws Exception {
		String postResult = mockMvc.perform(post("/api/v1/community/posts")
						.header("Authorization", aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new CreatePostRequest("Alice post to delete"))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long postId = objectMapper.readTree(postResult).get("id").asLong();

		// Bob tries to delete Alice's post
		mockMvc.perform(delete("/api/v1/community/posts/" + postId)
						.header("Authorization", bobToken))
				.andExpect(status().isForbidden());

		// Alice deletes her own post
		mockMvc.perform(delete("/api/v1/community/posts/" + postId)
						.header("Authorization", aliceToken))
				.andExpect(status().isNoContent());

		// Confirm deleted
		mockMvc.perform(get("/api/v1/community/posts/" + postId))
				.andExpect(status().isNotFound());
	}
}
