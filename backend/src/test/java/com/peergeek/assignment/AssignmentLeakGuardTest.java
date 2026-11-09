package com.peergeek.assignment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentLeakGuardTest extends AssignmentTestSupport {

	@Test
	@DisplayName("Learner DTOs strictly prohibit answer leakage, emails, object keys, and bucket names")
	void verifyLearnerDtoLeakGuard() throws Exception {
		UserEntity instructor = createInstructor("secret.prof@example.test", "Prof Secret");
		String instructorToken = tokenFor(instructor);
		long courseId = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, courseId);

		long assignmentId = createAssignment(instructorToken, courseId, "Strict Test", AssignmentKind.QUESTIONS);
		var q = buildMcqQuestion(1, "What is the answer?", 10, false, List.of(
				buildMcqOption("optA", "Secret Option A", true),
				buildMcqOption("optB", "Public Option B", false)
		));
		replaceQuestions(instructorToken, assignmentId, List.of(q));
		publishAssignment(instructorToken, assignmentId);

		UserEntity learner = createUser("learner.leak@example.test", "Learner Leak");
		String learnerToken = tokenFor(learner);
		enrollLearner(learnerToken, courseId);

		// 1. Assignment Detail before start: questions MUST NOT be present
		var detailRes = mockMvc.perform(get("/api/v1/learning/assignments/{assignmentId}", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.questions").doesNotExist())
				.andReturn();

		JsonNode detailJson = objectMapper.readTree(detailRes.getResponse().getContentAsString());
		assertNoLeak(detailJson);

		// 2. Start Attempt
		var startRes = mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/start", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andReturn();

		JsonNode startJson = objectMapper.readTree(startRes.getResponse().getContentAsString());
		assertNoLeak(startJson);

		// 3. Active Attempt: questions are present, but options MUST NOT contain 'correct' flag!
		var attemptRes = mockMvc.perform(get("/api/v1/learning/assignments/{assignmentId}/attempt", assignmentId)
						.header("Authorization", bearer(learnerToken)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.questions").isArray())
				.andReturn();

		JsonNode attemptJson = objectMapper.readTree(attemptRes.getResponse().getContentAsString());
		assertNoLeak(attemptJson);

		JsonNode questionOptions = attemptJson.get("questions").get(0).get("options");
		assertThat(questionOptions.isArray()).isTrue();
		for (JsonNode opt : questionOptions) {
			Set<String> keys = new HashSet<>();
			opt.fieldNames().forEachRemaining(keys::add);
			assertThat(keys).containsExactlyInAnyOrder("id", "text");
			assertThat(opt.has("correct")).isFalse();
		}

		// 4. Submit and check result: when showCorrectAnswers is false (default), answers are NOT leaked!
		ObjectNode draftReq = objectMapper.createObjectNode();
		draftReq.put("version", attemptJson.get("version").asLong());
		ArrayNode answers = draftReq.putArray("answers");
		ObjectNode a1 = answers.addObject();
		a1.put("questionId", attemptJson.get("questions").get(0).get("id").asLong());
		a1.putArray("selectedOptionIds").add("optA");

		var draftRes = mockMvc.perform(put("/api/v1/learning/assignments/{assignmentId}/draft", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(draftReq.toString()))
				.andExpect(status().isOk())
				.andReturn();
		long latestVersion = objectMapper.readTree(draftRes.getResponse().getContentAsString()).get("version").asLong();

		ObjectNode submitReq = objectMapper.createObjectNode();
		submitReq.put("version", latestVersion);

		var submitRes = mockMvc.perform(post("/api/v1/learning/assignments/{assignmentId}/submit", assignmentId)
						.header("Authorization", bearer(learnerToken))
						.contentType("application/json")
						.content(submitReq.toString()))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andReturn();

		JsonNode submitJson = objectMapper.readTree(submitRes.getResponse().getContentAsString());
		assertNoLeak(submitJson);
	}

	private void assertNoLeak(JsonNode root) {
		String serialized = root.toString();
		assertThat(serialized).doesNotContain("secret.prof@example.test");
		assertThat(serialized).doesNotContain("\"correct\":true");
		assertThat(serialized).doesNotContain("\"correct\":false");
		assertThat(serialized).doesNotContain("peergeek-test"); // bucket name
		assertThat(serialized).doesNotContain("X-Amz-Signature");
		assertThat(serialized).doesNotContain("X-Amz-Credential");
	}
}
