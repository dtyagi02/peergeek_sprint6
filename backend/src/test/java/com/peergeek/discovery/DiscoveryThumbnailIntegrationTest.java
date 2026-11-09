package com.peergeek.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class DiscoveryThumbnailIntegrationTest extends DiscoveryTestSupport {

	@Test
	void redirectsToFreshPresignedUrlWithShortTtlCacheControlWhenThumbnailReady() throws Exception {
		UserEntity instructor = createInstructor("thumb-inst@example.test", "Thumbnail Instructor");
		String token = tokenFor(instructor);
		long courseId = createReadyCourse(token, "Thumbnail Course", "Has a ready thumbnail");
		publishCourse(token, courseId);
		attachThumbnailResource(courseId, instructor.getId(), "READY");

		MvcResult result = mockMvc.perform(get("/api/v1/public/courses/{courseId}/thumbnail", courseId))
				.andExpect(status().isFound())
				.andExpect(header().string("Cache-Control", "public, max-age=60"))
				.andReturn();

		String location = result.getResponse().getHeader("Location");
		assertThat(location).isNotBlank();
		assertThat(location).contains("courses/" + courseId + "/thumb.png");
	}

	@Test
	void returnsNotFoundWhenCourseNotPublished() throws Exception {
		UserEntity instructor = createInstructor("thumb-unpub@example.test", "Unpub Instructor");
		String token = tokenFor(instructor);
		long courseId = createReadyCourse(token, "Unpublished Thumbnail Course", "Desc");
		attachThumbnailResource(courseId, instructor.getId(), "READY");

		// Course is in draft (not published)
		mockMvc.perform(get("/api/v1/public/courses/{courseId}/thumbnail", courseId))
				.andExpect(status().isNotFound());
	}

	@Test
	void returnsNotFoundWhenCourseHasNoThumbnail() throws Exception {
		UserEntity instructor = createInstructor("thumb-none@example.test", "No Thumb Instructor");
		String token = tokenFor(instructor);
		long courseId = createReadyCourse(token, "No Thumb Course", "Desc");
		publishCourse(token, courseId);

		mockMvc.perform(get("/api/v1/public/courses/{courseId}/thumbnail", courseId))
				.andExpect(status().isNotFound());
	}

	@Test
	void returnsNotFoundWhenThumbnailIsNotReady() throws Exception {
		UserEntity instructor = createInstructor("thumb-pending@example.test", "Pending Instructor");
		String token = tokenFor(instructor);
		long courseId = createReadyCourse(token, "Pending Thumb Course", "Desc");
		publishCourse(token, courseId);
		attachThumbnailResource(courseId, instructor.getId(), "PROCESSING");

		mockMvc.perform(get("/api/v1/public/courses/{courseId}/thumbnail", courseId))
				.andExpect(status().isNotFound());
	}
}
