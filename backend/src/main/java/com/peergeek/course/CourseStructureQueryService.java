package com.peergeek.course;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;

public interface CourseStructureQueryService {
	CourseStructure getStructure(long courseId);
	LessonContentSnapshot getLessonContent(long lessonId);
	Long getCourseOwnerId(long courseId);
	Optional<LessonLocation> getLessonLocation(long lessonId);
	Set<Long> findExistingLessonIds(Set<Long> lessonIds);
	Optional<CourseMetadataSnapshot> getCourseMetadata(long courseId);
	Map<Long, CourseMetadataSnapshot> getCourseMetadataBulk(Collection<Long> courseIds);
	Optional<PublishedCourseSnapshot> getPublishedCourse(long courseId);
	boolean isPublished(long courseId);
	List<Long> listPublishedCourseIds(Long afterId, int limit);

	record CourseStructure(CourseSummary course, List<ModuleSnapshot> modules) {
		public CourseStructure {
			modules = List.copyOf(modules);
		}
	}

	record CourseSummary(Long id, Long ownerId, String title, String description,
			CourseDifficulty difficulty, CourseStatus status, long version) {
	}

	record ModuleSnapshot(Long id, String title, String description, int position, List<LessonSnapshot> lessons) {
		public ModuleSnapshot {
			lessons = List.copyOf(lessons);
		}
	}

	record LessonSnapshot(Long id, String title, String summary, int position, int wordCount, boolean hasContent) {
	}

	record LessonContentSnapshot(String contentFormat, JsonNode document, int wordCount, long version, Instant updatedAt) {
		public LessonContentSnapshot {
			document = document.deepCopy();
		}
	}
}
