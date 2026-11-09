package com.peergeek.readiness;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import com.peergeek.config.ReadinessSettings;
import com.peergeek.course.CourseDifficulty;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStatus;
import com.peergeek.course.CourseStructureQueryService;
import org.junit.jupiter.api.Test;

class BasicReadinessRulesTest {
	@Test
	void reportsRequiredCourseFieldsAndMissingStructureAsBlocking() {
		CourseMetadataSnapshot metadata = new CourseMetadataSnapshot(1L, 2L, CourseStatus.DRAFT,
				false, 1, "", "", null, null, false, null, List.of(), null);
		var structure = new CourseStructureQueryService.CourseStructure(
				new CourseStructureQueryService.CourseSummary(1L, 2L, "", "", null,
						CourseStatus.DRAFT, 1), List.of());

		List<ReadinessIssue> issues = new BasicReadinessRules(new ReadinessSettings())
				.evaluate(new ReadinessContext(metadata, structure, List.of()));

		assertThat(issues).extracting(ReadinessIssue::ruleCode).contains(
				"COURSE_TITLE_REQUIRED", "COURSE_DESCRIPTION_REQUIRED", "COURSE_CATEGORY_REQUIRED",
				"COURSE_DIFFICULTY_REQUIRED", "COURSE_TOPIC_REQUIRED", "COURSE_MODULE_REQUIRED");
		assertThat(issues).allMatch(issue -> issue.severity() == ReadinessIssue.Severity.BLOCKING_ERROR);
	}

	@Test
	void doesNotFlagValidCourseMetadataOrAuthoredLessons() {
		CourseMetadataSnapshot metadata = new CourseMetadataSnapshot(1L, 2L, CourseStatus.DRAFT,
				false, 1, "Course title",
				"A sufficiently descriptive course description with details about the learning outcomes.", 3L,
				"Science", true, CourseDifficulty.BEGINNER, List.of("science"), null);
		var lesson = new CourseStructureQueryService.LessonSnapshot(9L, "Lesson", "Summary", 0, 42, true);
		var module = new CourseStructureQueryService.ModuleSnapshot(7L, "Module", "Description", 0,
				List.of(lesson));
		var structure = new CourseStructureQueryService.CourseStructure(
				new CourseStructureQueryService.CourseSummary(1L, 2L, "Course title", "Description",
						CourseDifficulty.BEGINNER, CourseStatus.DRAFT, 1), List.of(module));

		List<ReadinessIssue> issues = new BasicReadinessRules(new ReadinessSettings())
				.evaluate(new ReadinessContext(metadata, structure, List.of()));

		assertThat(issues).isEmpty();
	}

	@Test
	void detectsModuleWithoutLessonsAndLessonMissingContent() {
		CourseMetadataSnapshot metadata = new CourseMetadataSnapshot(1L, 2L, CourseStatus.DRAFT,
				false, 1, "Course title",
				"A sufficiently descriptive course description with details about the learning outcomes.", 3L,
				"Science", true, CourseDifficulty.BEGINNER, List.of("science"), null);

		var emptyLesson = new CourseStructureQueryService.LessonSnapshot(9L, "Empty Lesson", "Summary", 0, 0, false);
		var moduleWithEmptyLesson = new CourseStructureQueryService.ModuleSnapshot(7L, "Spring Security", "Description", 0,
				List.of(emptyLesson));
		var moduleWithoutLessons = new CourseStructureQueryService.ModuleSnapshot(8L, "Advanced Topics", "Description", 1,
				List.of());

		var structure = new CourseStructureQueryService.CourseStructure(
				new CourseStructureQueryService.CourseSummary(1L, 2L, "Course title", "Description",
						CourseDifficulty.BEGINNER, CourseStatus.DRAFT, 1),
				List.of(moduleWithEmptyLesson, moduleWithoutLessons));

		List<ReadinessIssue> issues = new BasicReadinessRules(new ReadinessSettings())
				.evaluate(new ReadinessContext(metadata, structure, List.of()));

		assertThat(issues).extracting(ReadinessIssue::ruleCode).contains(
				"MODULE_LESSON_REQUIRED", "LESSON_CONTENT_REQUIRED");

		ReadinessIssue moduleIssue = issues.stream()
				.filter(i -> "MODULE_LESSON_REQUIRED".equals(i.ruleCode()))
				.findFirst().orElseThrow();
		assertThat(moduleIssue.moduleId()).isEqualTo(8L);
		assertThat(moduleIssue.moduleTitle()).isEqualTo("Advanced Topics");
		assertThat(moduleIssue.message()).contains("Advanced Topics");

		ReadinessIssue lessonIssue = issues.stream()
				.filter(i -> "LESSON_CONTENT_REQUIRED".equals(i.ruleCode()))
				.findFirst().orElseThrow();
		assertThat(lessonIssue.lessonId()).isEqualTo(9L);
		assertThat(lessonIssue.lessonTitle()).isEqualTo("Empty Lesson");
		assertThat(lessonIssue.message()).contains("Empty Lesson");
	}
}
