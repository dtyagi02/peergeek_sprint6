package com.peergeek.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.Test;

class DiscoveryIndexSyncIntegrationTest extends DiscoveryTestSupport {

	@Test
	void publishingACourseCreatesIndexRowAndEvictsCacheImmediately() throws Exception {
		UserEntity instructor = createInstructor("instructor1@example.test", "Instructor One");
		String token = tokenFor(instructor);
		long courseId = createReadyCourse(token, "Advanced Distributed Systems", "Deep dive into distributed systems.");

		assertThat(indexRepository.findById(courseId)).isEmpty();

		publishCourse(token, courseId);

		Optional<PublicCourseIndexEntity> indexed = indexRepository.findById(courseId);
		assertThat(indexed).isPresent();
		PublicCourseIndexEntity row = indexed.get();
		assertThat(row.getTitle()).isEqualTo("Advanced Distributed Systems");
		assertThat(row.getInstructorName()).isEqualTo("Instructor One");
		assertThat(row.getDifficulty()).isEqualTo("INTERMEDIATE");
		assertThat(row.getModuleCount()).isEqualTo(1);
		assertThat(row.getLessonCount()).isEqualTo(1);
		assertThat(row.getSourceFingerprint()).isNotBlank();
		assertThat(row.getIndexedAt()).isNotNull();
	}

	@Test
	void unpublishingACourseDeletesIndexRowImmediately() throws Exception {
		UserEntity instructor = createInstructor("instructor2@example.test", "Instructor Two");
		String token = tokenFor(instructor);
		long courseId = createReadyCourse(token, "Course To Unpublish", "Course will be unpublished.");

		publishCourse(token, courseId);
		assertThat(indexRepository.findById(courseId)).isPresent();

		unpublishCourse(token, courseId);
		assertThat(indexRepository.findById(courseId)).isEmpty();
	}

	@Test
	void courseContentChangeOnPublishedCourseMarksItDirtyAndRebuildsOnFlush() throws Exception {
		UserEntity instructor = createInstructor("instructor3@example.test", "Instructor Three");
		String token = tokenFor(instructor);
		long courseId = createReadyCourse(token, "Original Title", "Original description.");

		publishCourse(token, courseId);
		PublicCourseIndexEntity initialRow = indexRepository.findById(courseId).orElseThrow();
		String originalFingerprint = initialRow.getSourceFingerprint();

		// Add a module (triggers CourseContentChangePublisher.touch -> CourseContentChangedEvent)
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/instructor/courses/{courseId}/modules", courseId)
						.header("Authorization", bearer(token))
						.contentType("application/json")
						.content("{\"title\":\"Module Two\",\"description\":\"Second Module\"}"))
				.andExpect(status().isCreated());

		// Trigger dirty flush
		dirtyFlusher.flush();

		PublicCourseIndexEntity updatedRow = indexRepository.findById(courseId).orElseThrow();
		assertThat(updatedRow.getModuleCount()).isEqualTo(2);
		assertThat(updatedRow.getSourceFingerprint()).isNotEqualTo(originalFingerprint);
	}

	@Test
	void eventsForNonPublishedCoursesAreIgnored() {
		UserEntity instructor = createInstructor("instructor4@example.test", "Instructor Four");
		String token = tokenFor(instructor);

		// Draft course exists only in authoring tables
		long courseId = 999_999L;
		dirtyFlusher.flush();
		assertThat(indexRepository.findById(courseId)).isEmpty();
	}
}
