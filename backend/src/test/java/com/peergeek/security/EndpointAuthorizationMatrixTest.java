package com.peergeek.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.peergeek.AbstractIntegrationTest;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
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
class EndpointAuthorizationMatrixTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private JwtService jwt;

	@Test
	@DisplayName("Public endpoints allow anonymous access")
	void publicEndpointsAllowAnonymousAccess() throws Exception {
		// Category listing is public
		mockMvc.perform(get("/api/v1/categories"))
				.andExpect(status().isOk());

		// Public course search is public
		mockMvc.perform(get("/api/v1/public/courses"))
				.andExpect(status().isOk());

		// Public popular courses is public
		mockMvc.perform(get("/api/v1/public/courses/popular"))
				.andExpect(status().isOk());

		// Actuator health and liveness are public (permitted without auth)
		mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().is(org.hamcrest.Matchers.isOneOf(200, 503)));

		// Actuator info is public
		mockMvc.perform(get("/actuator/info"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Every protected endpoint rejects anonymous calls with 401 UNAUTHORIZED")
	void protectedEndpointsRejectAnonymousAccess() throws Exception {
		String[] getEndpoints = {
				"/api/v1/users/me",
				"/api/v1/instructor/courses",
				"/api/v1/instructor/courses/1",
				"/api/v1/instructor/courses/1/analytics",
				"/api/v1/instructor/courses/1/readiness",
				"/api/v1/instructor/courses/1/assignments",
				"/api/v1/instructor/courses/1/reports",
				"/api/v1/learning/courses",
				"/api/v1/learning/courses/1",
				"/api/v1/learning/courses/1/enrollment",
				"/api/v1/learning/courses/1/continue",
				"/api/v1/learning/assignments/my",
				"/api/v1/learning/courses/1/assignments",
				"/api/v1/notifications",
				"/api/v1/notifications/unread-count",
				"/api/v1/bookmarks",
				"/api/v1/courses/1/bookmarks",
				"/api/v1/courses/1/lessons/1/bookmarks",
				"/api/v1/courses/1/lessons/1/notes",
				"/api/v1/courses/1/reviews/my"
		};

		for (String endpoint : getEndpoints) {
			mockMvc.perform(get(endpoint))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
					.andExpect(jsonPath("$.status").value(401));
		}

		// Mutating protected endpoints
		mockMvc.perform(post("/api/v1/auth/logout"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(patch("/api/v1/users/me")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"displayName\":\"Hacker\"}"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/instructor/courses")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Rogue Course\",\"categoryId\":1}"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/learning/courses/1/enroll"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(delete("/api/v1/learning/courses/1/enrollment"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/courses/1/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\":5,\"reviewText\":\"Great\"}"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/notifications/mark-all-read"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(patch("/api/v1/notifications/1/read"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(put("/api/v1/courses/1/lessons/1/notes")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"Private note\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Ownership check: Instructors cannot inspect or alter another instructor's courses")
	void instructorCannotAccessAnotherInstructorsCourse() throws Exception {
		UserEntity instructorA = users.saveAndFlush(
				new UserEntity("matrix-inst-a@example.test", "hash123", "Instructor A"));
		UserEntity instructorB = users.saveAndFlush(
				new UserEntity("matrix-inst-b@example.test", "hash123", "Instructor B"));

		String tokenA = jwt.issue(instructorA);
		String tokenB = jwt.issue(instructorB);

		// Instructor A creates a course
		String createJson = "{\"title\":\"Instructor A Course\",\"description\":\"Desc\",\"categoryId\":1,\"difficulty\":\"BEGINNER\",\"topics\":[\"java\"]}";
		String res = mockMvc.perform(post("/api/v1/instructor/courses")
						.header("Authorization", "Bearer " + tokenA)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createJson))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res);
		long courseId = json.get("id").asLong();

		// Instructor B attempts to read Instructor A's course -> 404 NOT_FOUND (indistinguishable)
		mockMvc.perform(get("/api/v1/instructor/courses/" + courseId)
						.header("Authorization", "Bearer " + tokenB))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		// Instructor B attempts to patch Instructor A's course -> 404 NOT_FOUND
		mockMvc.perform(patch("/api/v1/instructor/courses/" + courseId)
						.header("Authorization", "Bearer " + tokenB)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Hijacked Course\"}"))
				.andExpect(status().isNotFound());

		// Instructor B attempts to view Instructor A's analytics -> 404 NOT_FOUND
		mockMvc.perform(get("/api/v1/instructor/courses/" + courseId + "/analytics")
						.header("Authorization", "Bearer " + tokenB))
				.andExpect(status().isNotFound());

		// Instructor B attempts to delete Instructor A's course -> 404 NOT_FOUND
		mockMvc.perform(delete("/api/v1/instructor/courses/" + courseId)
						.header("Authorization", "Bearer " + tokenB))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Learner privacy: Learners cannot access another learner's private notes or tamper with notifications")
	void learnerCannotAccessOtherLearnersNotesOrNotifications() throws Exception {
		UserEntity learnerA = users.saveAndFlush(
				new UserEntity("matrix-learner-a@example.test", "hash123", "Learner A"));
		UserEntity learnerB = users.saveAndFlush(
				new UserEntity("matrix-learner-b@example.test", "hash123", "Learner B"));

		String tokenA = jwt.issue(learnerA);
		String tokenB = jwt.issue(learnerB);

		// Learner B attempts to read notes on a non-existent or unpublished course -> 404 NOT_FOUND (indistinguishability)
		mockMvc.perform(get("/api/v1/courses/999/lessons/999/notes")
						.header("Authorization", "Bearer " + tokenB))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		// Learner B attempts to mark an arbitrary notification ID of another user as read -> 404 NOT_FOUND (IDOR protected)
		mockMvc.perform(patch("/api/v1/notifications/999999/read")
						.header("Authorization", "Bearer " + tokenB))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}
}
