package com.peergeek.report;

import com.peergeek.enrollment.LearningTestSupport;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommunityReportIntegrationTest extends LearningTestSupport {

	@Test
	@DisplayName("Learner can report a course problem; instructor can view and resolve it")
	void learnerCanReportAndInstructorCanResolve() throws Exception {
		UserEntity instructor = createInstructor("prof.rep@test.local", "Prof Reporter Target");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		UserEntity learner = createUser("learner.rep@test.local", "Learner Reporter");
		String learnerToken = tokenFor(learner);

		// Submit report
		var createResult = mockMvc.perform(post("/api/v1/courses/{courseId}/reports", course.courseId())
						.header("Authorization", bearer(learnerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"category\": \"BROKEN_RESOURCE\", \"details\": \"Lesson video attachment is unplayable.\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.category").value("BROKEN_RESOURCE"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.details").value("Lesson video attachment is unplayable."))
				.andReturn();

		long reportId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

		// Instructor lists reports
		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/reports", course.courseId())
						.header("Authorization", bearer(instructorToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(reportId))
				.andExpect(jsonPath("$.content[0].reporterName").value("Learner Reporter"));

		// Instructor resolves report
		mockMvc.perform(put("/api/v1/instructor/reports/{reportId}/status", reportId)
						.header("Authorization", bearer(instructorToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\": \"RESOLVED\", \"resolutionNotes\": \"Re-encoded and updated the video resource.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RESOLVED"))
				.andExpect(jsonPath("$.resolutionNotes").value("Re-encoded and updated the video resource."));

		// Verify DB status
		String dbStatus = jdbc.queryForObject("select status from community_reports where id = ?", String.class, reportId);
		assertThat(dbStatus).isEqualTo("RESOLVED");
	}

	@Test
	@DisplayName("Non-instructor cannot view reports for another instructor's course -> 403 Forbidden")
	void nonInstructorCannotViewCourseReports() throws Exception {
		UserEntity instructor = createInstructor("prof.rep2@test.local", "Prof Target 2");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		UserEntity otherInstructor = createInstructor("other.prof@test.local", "Other Prof");
		String otherToken = tokenFor(otherInstructor);

		mockMvc.perform(get("/api/v1/instructor/courses/{courseId}/reports", course.courseId())
						.header("Authorization", bearer(otherToken)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Report requires valid category and non-blank details")
	void reportValidation() throws Exception {
		UserEntity instructor = createInstructor("prof.rep3@test.local", "Prof Target 3");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		UserEntity learner = createUser("learner.rep3@test.local", "Learner 3");
		String learnerToken = tokenFor(learner);

		// Missing category
		mockMvc.perform(post("/api/v1/courses/{courseId}/reports", course.courseId())
						.header("Authorization", bearer(learnerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"details\": \"Something is broken.\"}"))
				.andExpect(status().isBadRequest());

		// Blank details
		mockMvc.perform(post("/api/v1/courses/{courseId}/reports", course.courseId())
						.header("Authorization", bearer(learnerToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"category\": \"OTHER\", \"details\": \"  \"}"))
				.andExpect(status().isBadRequest());
	}
}
