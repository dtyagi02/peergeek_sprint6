package com.peergeek.readiness;

import java.util.ArrayList;
import java.util.List;
import com.peergeek.config.ReadinessSettings;
import com.peergeek.file.LessonResourceQueryService;
import org.springframework.stereotype.Component;

@Component
public class BasicReadinessRules implements ReadinessRule {
	private final ReadinessSettings settings;

	public BasicReadinessRules(ReadinessSettings settings) {
		this.settings = settings;
	}

	public List<ReadinessIssue> evaluate(ReadinessContext context) {
		List<ReadinessIssue> issues = new ArrayList<>();
		var course = context.metadata();
		if (course.title() == null || course.title().isBlank()) {
			issues.add(courseIssue("COURSE_TITLE_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
					"A course title is required.", "Add a course title."));
		}

		if (course.description() == null || course.description().isBlank()) {
			issues.add(courseIssue("COURSE_DESCRIPTION_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
					"A course description is required.", "Add a course description."));
		} else if (course.description().strip().length()
				< settings.getReport().getCourseDescriptionWarningMinLength()) {
			issues.add(courseIssue("COURSE_DESCRIPTION_SHORT", ReadinessIssue.Severity.WARNING,
					"The course description is brief.", "Consider adding more detail."));
		}
		if (course.categoryId() == null || !course.categoryActive()) {
			issues.add(courseIssue("COURSE_CATEGORY_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
					"An active course category is required.", "Choose an active category."));
		}
		if (course.difficulty() == null) {
			issues.add(courseIssue("COURSE_DIFFICULTY_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
					"A course difficulty is required.", "Set the course difficulty."));
		}
		if (course.topics().isEmpty()) {
			issues.add(courseIssue("COURSE_TOPIC_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
					"At least one course topic is required.", "Add one or more course topics."));
		}
		if (context.structure().modules().isEmpty()) {
			issues.add(courseIssue("COURSE_MODULE_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
					"The course must contain at least one module.", "Add a module and lessons."));
		}
		for (var module : context.structure().modules()) {
			if (module.title() == null || module.title().isBlank()) {
				issues.add(new ReadinessIssue("MODULE_TITLE_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
						ReadinessIssue.Scope.MODULE, module.id(), null, null, module.title(), null, null,
						"A module title is required.", "Add a title to this module.", null));
			}
			if (module.lessons().isEmpty()) {
				String modName = module.title() != null && !module.title().isBlank()
						? "'" + module.title() + "'"
						: "(ID " + module.id() + ")";
				issues.add(new ReadinessIssue("MODULE_LESSON_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
						ReadinessIssue.Scope.MODULE, module.id(), null, null, module.title(), null, null,
						"Module " + modName + " has no lessons.", "Add at least one lesson to this module.", null));
			}
			for (var lesson : module.lessons()) {
				if (lesson.title() == null || lesson.title().isBlank()) {
					issues.add(new ReadinessIssue("LESSON_TITLE_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
							ReadinessIssue.Scope.LESSON, module.id(), lesson.id(), null, module.title(),
							lesson.title(), null, "A lesson title is required.", "Add a title to this lesson.", null));
				}
				if (!lesson.hasContent() || lesson.wordCount() == 0) {
					String lesName = lesson.title() != null && !lesson.title().isBlank()
							? "'" + lesson.title() + "'"
							: "(ID " + lesson.id() + ")";
					issues.add(new ReadinessIssue("LESSON_CONTENT_REQUIRED", ReadinessIssue.Severity.BLOCKING_ERROR,
							ReadinessIssue.Scope.LESSON, module.id(), lesson.id(), null, module.title(),
							lesson.title(), null, "Lesson " + lesName + " has no required authored content.",
							"Add content to this lesson.", null));
				}
			}
		}
		for (LessonResourceQueryService.CourseLessonResourceState lesson : context.resources()) {
			for (var expected : lesson.expected()) {
				long ready = lesson.resources().stream()
						.filter(resource -> resource.type() == expected.resourceType()
								&& resource.status() == com.peergeek.file.ResourceStatus.READY)
						.count();
				if (ready < expected.minCount()) {
					issues.add(new ReadinessIssue("EXPECTED_RESOURCE_MISSING",
							ReadinessIssue.Severity.BLOCKING_ERROR, ReadinessIssue.Scope.LESSON,
							null, lesson.lessonId(), null, null, null, null,
							"Expected resource requirement is not satisfied: " + expected.label(),
							"Add the expected " + expected.resourceType().name().toLowerCase() + " resource.",
							java.util.Map.of("required", expected.minCount(), "ready", ready)));
				}
			}
			for (var resource : lesson.resources()) {
				if (resource.status() != com.peergeek.file.ResourceStatus.READY) {
					issues.add(new ReadinessIssue("RESOURCE_NOT_READY",
							ReadinessIssue.Severity.BLOCKING_ERROR, ReadinessIssue.Scope.RESOURCE,
							null, lesson.lessonId(), resource.id(), null, null, null,
							"Resource is not ready: " + resource.status().name().toLowerCase(),
							"Wait for processing to finish or replace the resource.",
							resource.failureReason() == null ? null
									: java.util.Map.of("failureReason", resource.failureReason())));
				}
			}
		}
		return List.copyOf(issues);
	}

	private static ReadinessIssue courseIssue(
			String code, ReadinessIssue.Severity severity, String message, String fixHint) {
		return new ReadinessIssue(code, severity, ReadinessIssue.Scope.COURSE, null, null, null,
				null, null, null, message, fixHint, null);
	}

	@Override
	public String code() {
		return "FAST";
	}

	@Override
	public RulePhase phase() {
		return RulePhase.FAST;
	}
}
