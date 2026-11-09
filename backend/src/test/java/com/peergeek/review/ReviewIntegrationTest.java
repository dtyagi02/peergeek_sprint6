package com.peergeek.review;

import java.util.List;
import com.peergeek.enrollment.LearningTestSupport;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewIntegrationTest extends LearningTestSupport {

	@Test
	@DisplayName("Enrolled student can submit and update a review, recomputing rating stats")
	void enrolledStudentCanSubmitAndUpdateReview() throws Exception {
		UserEntity instructor = createInstructor("prof.rev@test.local", "Prof Reviewer");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		UserEntity student = createUser("student.rev@test.local", "Student Reviewer");
		String studentToken = tokenFor(student);

		// Enroll student
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk());

		// Submit review
		mockMvc.perform(post("/api/v1/courses/{courseId}/reviews", course.courseId())
						.header("Authorization", bearer(studentToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\": 5, \"reviewText\": \"Incredible systems course!\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rating").value(5))
				.andExpect(jsonPath("$.reviewText").value("Incredible systems course!"))
				.andExpect(jsonPath("$.reviewerName").value("Student Reviewer"));

		// Verify DB persistence
		Integer count = jdbc.queryForObject(
				"select count(*) from reviews where course_id = ? and user_id = ? and deleted_at is null",
				Integer.class, course.courseId(), student.getId());
		assertThat(count).isEqualTo(1);

		// Get my review
		mockMvc.perform(get("/api/v1/courses/{courseId}/reviews/my", course.courseId())
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rating").value(5));

		// Public endpoint returns review
		mockMvc.perform(get("/api/v1/public/courses/{courseId}/reviews", course.courseId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].rating").value(5));

		// Update review (idempotent upsert)
		mockMvc.perform(post("/api/v1/courses/{courseId}/reviews", course.courseId())
						.header("Authorization", bearer(studentToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\": 4, \"reviewText\": \"Updated: Good course overall.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rating").value(4))
				.andExpect(jsonPath("$.reviewText").value("Updated: Good course overall."));

		// Still 1 review row
		count = jdbc.queryForObject(
				"select count(*) from reviews where course_id = ? and user_id = ? and deleted_at is null",
				Integer.class, course.courseId(), student.getId());
		assertThat(count).isEqualTo(1);
	}

	@Test
	@DisplayName("Unenrolled student cannot review a course -> 403 REVIEW_ENROLLMENT_REQUIRED")
	void unenrolledStudentCannotReview() throws Exception {
		UserEntity instructor = createInstructor("prof.rev2@test.local", "Prof Reviewer 2");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		UserEntity outsider = createUser("outsider.rev@test.local", "Outsider User");
		String outsiderToken = tokenFor(outsider);

		mockMvc.perform(post("/api/v1/courses/{courseId}/reviews", course.courseId())
						.header("Authorization", bearer(outsiderToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\": 3, \"reviewText\": \"Haven't taken it.\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("REVIEW_ENROLLMENT_REQUIRED"));
	}

	@Test
	@DisplayName("Course owner cannot review their own course -> 409 CANNOT_REVIEW_OWN_COURSE")
	void courseOwnerCannotReviewOwnCourse() throws Exception {
		UserEntity instructor = createInstructor("prof.rev3@test.local", "Prof Reviewer 3");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		mockMvc.perform(post("/api/v1/courses/{courseId}/reviews", course.courseId())
						.header("Authorization", bearer(instructorToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\": 5, \"reviewText\": \"I love my course.\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CANNOT_REVIEW_OWN_COURSE"));
	}

	@Test
	@DisplayName("Rating must be between 1 and 5")
	void ratingBoundsValidation() throws Exception {
		UserEntity instructor = createInstructor("prof.rev4@test.local", "Prof Reviewer 4");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		UserEntity student = createUser("student.rev4@test.local", "Student Reviewer 4");
		String studentToken = tokenFor(student);

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk());

		// Rating 0 -> 400
		mockMvc.perform(post("/api/v1/courses/{courseId}/reviews", course.courseId())
						.header("Authorization", bearer(studentToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\": 0, \"reviewText\": \"Too low\"}"))
				.andExpect(status().isBadRequest());

		// Rating 6 -> 400
		mockMvc.perform(post("/api/v1/courses/{courseId}/reviews", course.courseId())
						.header("Authorization", bearer(studentToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\": 6, \"reviewText\": \"Too high\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Course review summary returns exact counts, averages, and rating distribution")
	void reviewSummaryAndDistributionStats() throws Exception {
		UserEntity instructor = createInstructor("prof.stats@test.local", "Prof Stats");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		// 1. Initial empty summary
		mockMvc.perform(get("/api/v1/public/courses/{courseId}/reviews/summary", course.courseId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.courseId").value(course.courseId()))
				.andExpect(jsonPath("$.averageRating").value(0.0))
				.andExpect(jsonPath("$.totalReviews").value(0))
				.andExpect(jsonPath("$.distribution.5").value(0))
				.andExpect(jsonPath("$.distribution.4").value(0))
				.andExpect(jsonPath("$.distribution.3").value(0))
				.andExpect(jsonPath("$.distribution.2").value(0))
				.andExpect(jsonPath("$.distribution.1").value(0));

		// 2. Submit reviews from two distinct enrolled students
		UserEntity student1 = createUser("s1.stats@test.local", "Student One");
		String token1 = tokenFor(student1);
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
						.header("Authorization", bearer(token1)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/courses/{courseId}/reviews", course.courseId())
						.header("Authorization", bearer(token1))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\": 5, \"reviewText\": \"Outstanding!\"}"))
				.andExpect(status().isOk());

		UserEntity student2 = createUser("s2.stats@test.local", "Student Two");
		String token2 = tokenFor(student2);
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
						.header("Authorization", bearer(token2)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/courses/{courseId}/reviews", course.courseId())
						.header("Authorization", bearer(token2))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\": 4, \"reviewText\": \"Very good course!\"}"))
				.andExpect(status().isOk());

		// 3. Verify summary calculation (avg = 4.5, total = 2, 5-star = 1 (50%), 4-star = 1 (50%))
		mockMvc.perform(get("/api/v1/public/courses/{courseId}/reviews/summary", course.courseId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.averageRating").value(4.5))
				.andExpect(jsonPath("$.totalReviews").value(2))
				.andExpect(jsonPath("$.distribution.5").value(1))
				.andExpect(jsonPath("$.distribution.4").value(1))
				.andExpect(jsonPath("$.distribution.3").value(0))
				.andExpect(jsonPath("$.distributionPercentages.5").value(50.0))
				.andExpect(jsonPath("$.distributionPercentages.4").value(50.0));
	}

	@Test
	@DisplayName("Review search, sorting, and rating filters behave correctly")
	void reviewSearchSortingAndFiltering() throws Exception {
		UserEntity instructor = createInstructor("prof.filter@test.local", "Prof Filter");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		UserEntity s1 = createUser("s1.flt@test.local", "Alice Student");
		String t1 = tokenFor(s1);
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
						.header("Authorization", bearer(t1)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/courses/{courseId}/reviews", course.courseId())
						.header("Authorization", bearer(t1))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\": 5, \"reviewText\": \"Brilliant compiler internals and explanations\"}"))
				.andExpect(status().isOk());

		UserEntity s2 = createUser("s2.flt@test.local", "Bob Student");
		String t2 = tokenFor(s2);
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
						.header("Authorization", bearer(t2)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/courses/{courseId}/reviews", course.courseId())
						.header("Authorization", bearer(t2))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\": 2, \"reviewText\": \"Too fast and complex pacing\"}"))
				.andExpect(status().isOk());

		UserEntity s3 = createUser("s3.flt@test.local", "Charlie Student");
		String t3 = tokenFor(s3);
		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
						.header("Authorization", bearer(t3)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/courses/{courseId}/reviews", course.courseId())
						.header("Authorization", bearer(t3))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\": 4, \"reviewText\": \"Solid compiler exercises and labs\"}"))
				.andExpect(status().isOk());

		// 1. Sort highest rating first
		mockMvc.perform(get("/api/v1/public/courses/{courseId}/reviews?sort=highest", course.courseId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].rating").value(5))
				.andExpect(jsonPath("$.content[1].rating").value(4))
				.andExpect(jsonPath("$.content[2].rating").value(2));

		// 2. Sort lowest rating first
		mockMvc.perform(get("/api/v1/public/courses/{courseId}/reviews?sort=lowest", course.courseId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].rating").value(2))
				.andExpect(jsonPath("$.content[1].rating").value(4))
				.andExpect(jsonPath("$.content[2].rating").value(5));

		// 3. Filter by rating = 5
		mockMvc.perform(get("/api/v1/public/courses/{courseId}/reviews?rating=5", course.courseId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].rating").value(5))
				.andExpect(jsonPath("$.content[0].reviewerName").value("Alice Student"));

		// 4. Search by review text
		mockMvc.perform(get("/api/v1/public/courses/{courseId}/reviews?search=compiler", course.courseId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2));

		mockMvc.perform(get("/api/v1/public/courses/{courseId}/reviews?search=complex", course.courseId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].rating").value(2));

		// 5. Search by reviewer name
		mockMvc.perform(get("/api/v1/public/courses/{courseId}/reviews?search=Alice", course.courseId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].reviewerName").value("Alice Student"));
	}

	@Test
	@DisplayName("Review deletion updates summary and recalculates stats")
	void reviewDeletionUpdatesSummaryAndStats() throws Exception {
		UserEntity instructor = createInstructor("prof.del@test.local", "Prof Del");
		String instructorToken = tokenFor(instructor);
		CourseCreationResult course = createCourseWithModulesAndLessons(instructorToken, 1, 1);
		publishCourse(instructorToken, course.courseId());

		UserEntity student = createUser("student.del@test.local", "Student Del");
		String studentToken = tokenFor(student);

		mockMvc.perform(post("/api/v1/learning/courses/{courseId}/enroll", course.courseId())
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/courses/{courseId}/reviews", course.courseId())
						.header("Authorization", bearer(studentToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rating\": 5, \"reviewText\": \"Will be deleted\"}"))
				.andExpect(status().isOk());

		// Verify 1 review in summary
		mockMvc.perform(get("/api/v1/public/courses/{courseId}/reviews/summary", course.courseId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalReviews").value(1))
				.andExpect(jsonPath("$.averageRating").value(5.0));

		// Delete review (returns 204 No Content)
		mockMvc.perform(delete("/api/v1/courses/{courseId}/reviews", course.courseId())
						.header("Authorization", bearer(studentToken)))
				.andExpect(status().isNoContent());

		// Verify 0 reviews in summary
		mockMvc.perform(get("/api/v1/public/courses/{courseId}/reviews/summary", course.courseId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalReviews").value(0))
				.andExpect(jsonPath("$.averageRating").value(0.0))
				.andExpect(jsonPath("$.distribution.5").value(0));
	}
}
