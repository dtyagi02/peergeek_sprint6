package com.peergeek.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class DiscoverySecurityAndLeakIntegrationTest extends DiscoveryTestSupport {

	@Test
	void publicLeakGuardEnforcesExactKeySetAndPreventsAnyDataLeaks() throws Exception {
		UserEntity instructor = createInstructor("leak-guard@example.test", "Guarded Instructor");
		String token = tokenFor(instructor);
		long courseId = createReadyCourse(token, "Secure Distributed Architecture", "Deep dive description with secret keywords.");
		publishCourse(token, courseId);
		long resourceId = attachThumbnailResource(courseId, instructor.getId(), "READY");
		indexService.indexCourse(courseId);

		// Record stats so stats sub-object is populated
		jdbc.update("insert into course_public_stats (course_id, enrollment_count, completion_count, rating_average, rating_count, updated_at) values (?, 20, 15, 4.75, 8, now())",
				courseId);

		// 1. Check CoursePreviewDto exact keys and absence of leaks
		MvcResult previewResult = mockMvc.perform(get("/api/v1/public/courses/{courseId}", courseId))
				.andExpect(status().isOk())
				.andReturn();

		String previewJson = previewResult.getResponse().getContentAsString();
		JsonNode previewNode = objectMapper.readTree(previewJson);

		assertThat(getFieldNames(previewNode)).containsExactlyInAnyOrder(
				"id", "title", "shortDescription", "thumbnailUrl", "category",
				"difficulty", "instructor", "moduleCount", "lessonCount",
				"publishedAt", "stats", "description", "topics", "outline"
		);

		assertThat(getFieldNames(previewNode.get("category"))).containsExactlyInAnyOrder("id", "name", "slug");
		assertThat(getFieldNames(previewNode.get("instructor"))).containsExactlyInAnyOrder("id", "displayName", "headline", "bio");
		assertThat(getFieldNames(previewNode.get("stats"))).containsExactlyInAnyOrder(
				"enrollmentCount", "completionCount", "completionRate", "ratingAverage", "ratingCount"
		);

		JsonNode modules = previewNode.get("outline");
		assertThat(modules.isArray()).isTrue();
		assertThat(modules.size()).isPositive();
		JsonNode module0 = modules.get(0);
		assertThat(getFieldNames(module0)).containsExactlyInAnyOrder("moduleTitle", "lessons");

		JsonNode lessons0 = module0.get("lessons");
		assertThat(lessons0.isArray()).isTrue();
		assertThat(lessons0.size()).isPositive();
		JsonNode lesson0 = lessons0.get(0);
		assertThat(getFieldNames(lesson0)).containsExactlyInAnyOrder("title");

		// Strict leak prevention assertions on preview JSON
		assertThat(previewJson)
				.doesNotContain("Protected lesson body text")
				.doesNotContain("leak-guard@example.test")
				.doesNotContain("PUBLISHED")
				.doesNotContain("DRAFT")
				.doesNotContain("thumb.png")
				.doesNotContain("peergeek-test")
				.doesNotContain("X-Amz-Signature")
				.doesNotContain("Signature=")
				.doesNotContain("version")
				.doesNotContain("wordCount");

		// 2. Check CourseCardDto exact keys in /courses search
		MvcResult listResult = mockMvc.perform(get("/api/v1/public/courses"))
				.andExpect(status().isOk())
				.andReturn();

		String listJson = listResult.getResponse().getContentAsString();
		JsonNode listNode = objectMapper.readTree(listJson);
		JsonNode content = listNode.get("content");
		assertThat(content.isArray()).isTrue();
		assertThat(content.size()).isPositive();

		JsonNode cardNode = content.get(0);
		assertThat(getFieldNames(cardNode)).containsExactlyInAnyOrder(
				"id", "title", "shortDescription", "thumbnailUrl", "category",
				"difficulty", "instructor", "moduleCount", "lessonCount",
				"publishedAt", "stats"
		);
		assertThat(getFieldNames(cardNode.get("category"))).containsExactlyInAnyOrder("id", "name", "slug");
		assertThat(getFieldNames(cardNode.get("instructor"))).containsExactlyInAnyOrder("id", "displayName");
		assertThat(getFieldNames(cardNode.get("stats"))).containsExactlyInAnyOrder(
				"enrollmentCount", "completionCount", "completionRate", "ratingAverage", "ratingCount"
		);

		assertThat(listJson)
				.doesNotContain("Protected lesson body text")
				.doesNotContain("leak-guard@example.test")
				.doesNotContain("PUBLISHED")
				.doesNotContain("DRAFT")
				.doesNotContain("peergeek-test")
				.doesNotContain("thumb.png")
				.doesNotContain("version")
				.doesNotContain("wordCount");
	}

	@Test
	void notPublishedCoursesAreIndistinguishableFromNonexistent() throws Exception {
		UserEntity instructor = createInstructor("indistinguish@example.test", "Indistinguishable");
		String token = tokenFor(instructor);

		// 1. Draft course
		long draftId = createReadyCourse(token, "Draft Course", "Draft desc");

		// 2. Unpublished course
		long unpublishedId = createReadyCourse(token, "Unpublished Course", "Unpublished desc");
		publishCourse(token, unpublishedId);
		unpublishCourse(token, unpublishedId);

		// 3. Deleted course
		long deletedId = createReadyCourse(token, "Deleted Course", "Deleted desc");
		publishCourse(token, deletedId);
		jdbc.update("update courses set deleted_at = now() where id = ?", deletedId);
		dirtyFlusher.flush();

		// 4. Nonexistent course
		long nonexistentId = 777_777L;

		// Verify 404 for preview
		MvcResult draftRes = mockMvc.perform(get("/api/v1/public/courses/{id}", draftId))
				.andExpect(status().isNotFound()).andReturn();
		MvcResult unpubRes = mockMvc.perform(get("/api/v1/public/courses/{id}", unpublishedId))
				.andExpect(status().isNotFound()).andReturn();
		MvcResult delRes = mockMvc.perform(get("/api/v1/public/courses/{id}", deletedId))
				.andExpect(status().isNotFound()).andReturn();
		MvcResult nonRes = mockMvc.perform(get("/api/v1/public/courses/{id}", nonexistentId))
				.andExpect(status().isNotFound()).andReturn();

		assertSanitizedErrorBodiesEqual(draftRes, unpubRes);
		assertSanitizedErrorBodiesEqual(draftRes, delRes);
		assertSanitizedErrorBodiesEqual(draftRes, nonRes);

		// Verify 404 for thumbnail
		MvcResult draftThumb = mockMvc.perform(get("/api/v1/public/courses/{id}/thumbnail", draftId))
				.andExpect(status().isNotFound()).andReturn();
		MvcResult unpubThumb = mockMvc.perform(get("/api/v1/public/courses/{id}/thumbnail", unpublishedId))
				.andExpect(status().isNotFound()).andReturn();
		MvcResult delThumb = mockMvc.perform(get("/api/v1/public/courses/{id}/thumbnail", deletedId))
				.andExpect(status().isNotFound()).andReturn();
		MvcResult nonThumb = mockMvc.perform(get("/api/v1/public/courses/{id}/thumbnail", nonexistentId))
				.andExpect(status().isNotFound()).andReturn();

		assertSanitizedErrorBodiesEqual(draftThumb, unpubThumb);
		assertSanitizedErrorBodiesEqual(draftThumb, delThumb);
		assertSanitizedErrorBodiesEqual(draftThumb, nonThumb);
	}

	@Test
	void warmCacheDoesNotServeUnpublishedCourse() throws Exception {
		UserEntity instructor = createInstructor("cache-unpub@example.test", "Cache Unpub");
		String token = tokenFor(instructor);
		long courseId = createReadyCourse(token, "Cache Course", "Cache description");
		publishCourse(token, courseId);

		// Prime cache
		mockMvc.perform(get("/api/v1/public/courses/{id}", courseId))
				.andExpect(status().isOk());

		// Unpublish course
		unpublishCourse(token, courseId);

		// Must return 404 immediately, never serving from cache
		mockMvc.perform(get("/api/v1/public/courses/{id}", courseId))
				.andExpect(status().isNotFound());
	}

	@Test
	void publicEndpointsIgnoreTokenAndPrivateEndpointsRemainProtected() throws Exception {
		UserEntity user = createInstructor("anon-test@example.test", "Anon Tester");
		String token = tokenFor(user);

		// Public endpoint without token
		MvcResult anon = mockMvc.perform(get("/api/v1/public/courses"))
				.andExpect(status().isOk())
				.andReturn();

		// Public endpoint with token
		MvcResult auth = mockMvc.perform(get("/api/v1/public/courses")
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andReturn();

		// Both return identical JSON structure
		assertThat(anon.getResponse().getContentAsString()).isEqualTo(auth.getResponse().getContentAsString());

		// Random non-allowlisted endpoint under /api/v1/public/ requires authentication
		mockMvc.perform(get("/api/v1/public/nonexistent-feature"))
				.andExpect(status().isUnauthorized());

		// POST to public course list is not permitted anonymously
		mockMvc.perform(post("/api/v1/public/courses"))
				.andExpect(status().isUnauthorized());

		// Private endpoint remains protected
		mockMvc.perform(get("/api/v1/instructor/courses"))
				.andExpect(status().isUnauthorized());
	}

	private List<String> getFieldNames(JsonNode node) {
		List<String> names = new ArrayList<>();
		Iterator<String> iterator = node.fieldNames();
		while (iterator.hasNext()) {
			names.add(iterator.next());
		}
		Collections.sort(names);
		return names;
	}

	private void assertSanitizedErrorBodiesEqual(MvcResult res1, MvcResult res2) throws Exception {
		JsonNode node1 = objectMapper.readTree(res1.getResponse().getContentAsString());
		JsonNode node2 = objectMapper.readTree(res2.getResponse().getContentAsString());

		// Remove dynamic timestamp/requestId/path fields if present
		if (node1.isObject()) {
			((ObjectNode) node1).remove(List.of("timestamp", "requestId", "path"));
		}
		if (node2.isObject()) {
			((ObjectNode) node2).remove(List.of("timestamp", "requestId", "path"));
		}

		assertThat(node1).isEqualTo(node2);
	}
}
