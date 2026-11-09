package com.peergeek.course;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peergeek.AbstractIntegrationTest;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseAuthoringIntegrationTest extends AbstractIntegrationTest {
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private UserRepository users;
	@Autowired
	private JwtService jwt;
	@Autowired
	private CourseStructureQueryService structureQuery;
	@Autowired
	private CourseThumbnailAccessor thumbnails;
	@Autowired
	private CourseStatusAccessor statuses;

	@BeforeEach
	void clearAuthoringData() {
		jdbc.update("delete from audit_logs");
		jdbc.update("delete from lesson_contents");
		jdbc.update("delete from lessons");
		jdbc.update("delete from course_modules");
		jdbc.update("delete from course_topics");
		jdbc.update("delete from courses");
		jdbc.update("delete from refresh_tokens");
		jdbc.update("delete from password_reset_otps");
		jdbc.update("delete from users");
	}

	@Test
	void activeCategoriesArePubliclyReadable() throws Exception {
		mockMvc.perform(get("/api/v1/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").isNumber());
	}

	@Test
	void lessonLocationIncludesSoftDeletedCourseAndBulkLookupReturnsOnlyExistingIds() throws Exception {
		String token = tokenFor("lookup@example.test");
		long courseId = createCourse(token, "Lookup course");
		long moduleId = createModule(token, courseId, "Lookup module");
		long lessonId = createLesson(token, moduleId, "Lookup lesson");

		org.assertj.core.api.Assertions.assertThat(structureQuery.getLessonLocation(lessonId))
				.contains(new LessonLocation(lessonId, moduleId, courseId,
						users.findByEmail("lookup@example.test").orElseThrow().getId(), false));
		org.assertj.core.api.Assertions.assertThat(structureQuery.getLessonLocation(999_999L)).isEmpty();
		org.assertj.core.api.Assertions.assertThat(
				structureQuery.findExistingLessonIds(Set.of(lessonId, 999_999L)))
				.containsExactly(lessonId);
		org.assertj.core.api.Assertions.assertThat(structureQuery.findExistingLessonIds(Set.of()))
				.isEmpty();

		jdbc.update("update courses set deleted_at = current_timestamp where id = ?", courseId);
		org.assertj.core.api.Assertions.assertThat(structureQuery.getLessonLocation(lessonId))
				.contains(new LessonLocation(lessonId, moduleId, courseId,
						users.findByEmail("lookup@example.test").orElseThrow().getId(), true));
		org.assertj.core.api.Assertions.assertThat(
				structureQuery.findExistingLessonIds(Set.of(lessonId, 999_999L)))
				.containsExactly(lessonId);
	}

	@Test
	void thumbnailAccessorReadsAndWritesOnlyTheExistingCourseColumn() throws Exception {
		String token = tokenFor("thumbnail@example.test");
		long courseId = createCourse(token, "Thumbnail course");
		long resourceId = 812L;
		long originalVersion = jdbc.queryForObject(
				"select version from courses where id = ?", Long.class, courseId);
		java.sql.Timestamp originalUpdatedAt = jdbc.queryForObject(
				"select updated_at from courses where id = ?", java.sql.Timestamp.class, courseId);

		org.assertj.core.api.Assertions.assertThat(thumbnails.getThumbnailResourceId(courseId))
				.isEmpty();
		thumbnails.setThumbnailResourceId(courseId, resourceId);

		org.assertj.core.api.Assertions.assertThat(thumbnails.getThumbnailResourceId(courseId))
				.contains(resourceId);
		org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
				"select count(*) from courses where id = ? and thumbnail_resource_id = ?",
				Integer.class, courseId, resourceId)).isEqualTo(1);
		org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
				"select version from courses where id = ?", Long.class, courseId))
				.isEqualTo(originalVersion);
		org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
				"select updated_at from courses where id = ?", java.sql.Timestamp.class, courseId))
				.isEqualTo(originalUpdatedAt);
		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> thumbnails.setThumbnailResourceId(999_999L, resourceId))
				.isInstanceOf(com.peergeek.exception.NotFoundException.class);
		thumbnails.setThumbnailResourceId(courseId, null);
		org.assertj.core.api.Assertions.assertThat(thumbnails.getThumbnailResourceId(courseId))
				.isEmpty();
		org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
				"select count(*) from courses where id = ? and thumbnail_resource_id is null",
				Integer.class, courseId)).isEqualTo(1);
	}

	@Test
	void statusAccessorPerformsConditionalTransitionsAndMetadataSnapshot() throws Exception {
		String token = tokenFor("status@example.test");
		long courseId = createCourse(token, "Status course");
		long moduleId = createModule(token, courseId, "Status module");
		createLesson(token, moduleId, "Status lesson");
		java.time.Instant publishedAt = java.time.Instant.parse("2026-10-16T12:00:00Z");

		org.assertj.core.api.Assertions.assertThat(statuses.getStatusSnapshot(courseId))
				.get().extracting(CourseStatusSnapshot::status).isEqualTo(CourseStatus.DRAFT);
		org.assertj.core.api.Assertions.assertThat(structureQuery.getCourseMetadata(courseId))
				.get()
				.satisfies(metadata -> {
					org.assertj.core.api.Assertions.assertThat(metadata.ownerId())
							.isEqualTo(users.findByEmail("status@example.test").orElseThrow().getId());
					org.assertj.core.api.Assertions.assertThat(metadata.status()).isEqualTo(CourseStatus.DRAFT);
					org.assertj.core.api.Assertions.assertThat(metadata.deleted()).isFalse();
					org.assertj.core.api.Assertions.assertThat(metadata.topics()).isEmpty();
					org.assertj.core.api.Assertions.assertThat(metadata.thumbnailResourceId()).isNull();
				});
		long expectedVersion = statuses.getStatusSnapshot(courseId).orElseThrow().version() + 1;
		org.assertj.core.api.Assertions.assertThat(statuses.transitionStatus(courseId,
				Set.of(CourseStatus.DRAFT), CourseStatus.PUBLISHED, publishedAt)).isEqualTo(1);
		org.assertj.core.api.Assertions.assertThat(statuses.getStatusSnapshot(courseId))
				.get().satisfies(snapshot -> {
					org.assertj.core.api.Assertions.assertThat(snapshot.status()).isEqualTo(CourseStatus.PUBLISHED);
					org.assertj.core.api.Assertions.assertThat(snapshot.version()).isEqualTo(expectedVersion);
				});
		org.assertj.core.api.Assertions.assertThat(statuses.transitionStatus(courseId,
				Set.of(CourseStatus.DRAFT), CourseStatus.UNPUBLISHED, null)).isZero();
		org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
				"select date_format(published_at, '%Y-%m-%dT%H:%i:%s') from courses where id = ?",
				String.class, courseId))
				.isEqualTo(publishedAt.toString().substring(0, 19));
	}

	@Test
	void lessonContentRoundTripsMoreThanTenThousandWordsAndRejectsStaleVersions() throws Exception {
		String token = tokenFor("author@example.test");
		long courseId = createCourse(token, "Content round trip");
		long moduleId = createModule(token, courseId, "Module one");
		long lessonId = createLesson(token, moduleId, "Lesson one");
		String text = "word ".repeat(10_001).trim();
		ObjectNode document = contentDocument(text);
		String contentRequest = contentRequest(document, 0);

		mockMvc.perform(put("/api/v1/instructor/lessons/{lessonId}/content", lessonId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(contentRequest))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wordCount").value(10_001))
				.andExpect(jsonPath("$.document.content[0].content[0].text").value(text));
		mockMvc.perform(get("/api/v1/instructor/lessons/{lessonId}/content", lessonId)
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wordCount").value(10_001))
				.andExpect(jsonPath("$.document.content[0].content[0].text").value(text));

		ObjectNode editedDocument = contentDocument(text + " revised");
		String editRequest = contentRequest(editedDocument, 0);
		mockMvc.perform(put("/api/v1/instructor/lessons/{lessonId}/content", lessonId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(editRequest))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wordCount").value(10_002))
				.andExpect(jsonPath("$.version").value(1));
		mockMvc.perform(put("/api/v1/instructor/lessons/{lessonId}/content", lessonId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(contentRequest))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void everyAuthoringResourceOperationIsScopedToItsCourseOwner() throws Exception {
		String ownerToken = tokenFor("owner@example.test");
		String otherToken = tokenFor("other@example.test");
		long courseId = createCourse(ownerToken, "Private draft");
		long moduleId = createModule(ownerToken, courseId, "Private module");
		long lessonId = createLesson(ownerToken, moduleId, "Private lesson");
		long secondModuleId = createModule(ownerToken, courseId, "Second module");
		long secondLessonId = createLesson(ownerToken, moduleId, "Second lesson");

		mockMvc.perform(put("/api/v1/instructor/courses/{courseId}/modules/order", courseId)
						.header("Authorization", bearer(ownerToken))
						.contentType("application/json")
						.content("{\"moduleIds\":[" + secondModuleId + "," + moduleId + "]}"))
				.andExpect(status().isNoContent());
		mockMvc.perform(put("/api/v1/instructor/modules/{moduleId}/lessons/order", moduleId)
						.header("Authorization", bearer(ownerToken))
						.contentType("application/json")
						.content("{\"lessonIds\":[" + secondLessonId + "," + lessonId + "]}"))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/instructor/lessons/{lessonId}/move", secondLessonId)
						.header("Authorization", bearer(ownerToken))
						.contentType("application/json")
						.content("{\"targetModuleId\":" + secondModuleId + ",\"position\":0}"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/outline", courseId)
						.header("Authorization", bearer(ownerToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.modules[0].id").value(secondModuleId))
				.andExpect(jsonPath("$.modules[0].lessons[0].id").value(secondLessonId))
				.andExpect(jsonPath("$.modules[1].id").value(moduleId))
				.andExpect(jsonPath("$.modules[1].lessons[0].id").value(lessonId));

		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}", courseId)
						.header("Authorization", bearer(otherToken)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/outline", courseId)
						.header("Authorization", bearer(otherToken)))
				.andExpect(status().isNotFound());
		mockMvc.perform(patch("/api/v1/instructor/courses/{courseId}", courseId)
						.header("Authorization", bearer(otherToken))
						.contentType("application/json").content("{\"version\":0,\"title\":\"Changed\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/instructor/courses/{courseId}", courseId)
						.header("Authorization", bearer(otherToken)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/instructor/courses")
						.header("Authorization", bearer(otherToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/modules", courseId)
						.header("Authorization", bearer(otherToken))
						.contentType("application/json").content("{\"title\":\"Intruder module\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/v1/instructor/courses/{courseId}/modules/order", courseId)
						.header("Authorization", bearer(otherToken))
						.contentType("application/json").content("{\"moduleIds\":[" + moduleId + "]}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(patch("/api/v1/instructor/modules/{moduleId}", moduleId)
						.header("Authorization", bearer(otherToken))
						.contentType("application/json").content("{\"title\":\"Changed\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/instructor/modules/{moduleId}", moduleId)
						.header("Authorization", bearer(otherToken)))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/instructor/modules/{moduleId}/lessons", moduleId)
						.header("Authorization", bearer(otherToken))
						.contentType("application/json").content("{\"title\":\"Intruder lesson\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(patch("/api/v1/instructor/lessons/{lessonId}", lessonId)
						.header("Authorization", bearer(otherToken))
						.contentType("application/json").content("{\"title\":\"Changed\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/instructor/lessons/{lessonId}", lessonId)
						.header("Authorization", bearer(otherToken)))
				.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/v1/instructor/modules/{moduleId}/lessons/order", moduleId)
						.header("Authorization", bearer(otherToken))
						.contentType("application/json").content("{\"lessonIds\":[" + lessonId + "]}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/instructor/lessons/{lessonId}/move", lessonId)
						.header("Authorization", bearer(otherToken))
						.contentType("application/json")
						.content("{\"targetModuleId\":" + moduleId + ",\"position\":0}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/instructor/lessons/{lessonId}/content", lessonId)
						.header("Authorization", bearer(otherToken)))
				.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/v1/instructor/lessons/{lessonId}/content", lessonId)
						.header("Authorization", bearer(otherToken))
						.contentType("application/json")
						.content("{\"document\":{\"type\":\"doc\",\"content\":[]},\"baseVersion\":0}"))
				.andExpect(status().isNotFound());
	}

	private long createCourse(String token, String title) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/instructor/courses")
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(objectMapper.createObjectNode().put("title", title).toString()))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	private ObjectNode contentDocument(String text) {
		ObjectNode document = objectMapper.createObjectNode().put("type", "doc");
		document.putArray("content").add(objectMapper.createObjectNode().put("type", "paragraph")
				.set("content", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
						.put("type", "text").put("text", text))));
		return document;
	}

	private String contentRequest(ObjectNode document, long baseVersion) {
		ObjectNode request = objectMapper.createObjectNode();
		request.set("document", document);
		request.put("baseVersion", baseVersion);
		return request.toString();
	}

	private long createModule(String token, long courseId, String title) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/instructor/courses/{courseId}/modules", courseId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(objectMapper.createObjectNode().put("title", title).toString()))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	private long createLesson(String token, long moduleId, String title) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/instructor/modules/{moduleId}/lessons", moduleId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content(objectMapper.createObjectNode().put("title", title).toString()))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	private String tokenFor(String email) {
		UserEntity user = users.saveAndFlush(new UserEntity(email, "test-password-hash", email));
		return jwt.issue(user);
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}
}
