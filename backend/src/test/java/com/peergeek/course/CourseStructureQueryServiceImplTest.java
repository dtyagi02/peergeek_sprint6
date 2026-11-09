package com.peergeek.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import com.peergeek.lesson.LessonContentRepository;
import com.peergeek.lesson.LessonOutlineRow;
import com.peergeek.lesson.LessonRepository;
import com.peergeek.module.ModuleEntity;
import com.peergeek.module.ModuleRepository;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.Test;

class CourseStructureQueryServiceImplTest {
	@Test
	void returnsCourseMetadataIncludingTopicsAndSoftDeletionWithoutAuthorization() {
		CourseRepository courses = mock(CourseRepository.class);
		ModuleRepository modules = mock(ModuleRepository.class);
		LessonRepository lessons = mock(LessonRepository.class);
		LessonContentRepository contents = mock(LessonContentRepository.class);
		when(courses.findCourseMetadataRows(8L)).thenReturn(List.of(
				new CourseMetadataRow(8L, 4L, CourseStatus.PUBLISHED, true, 9L, "Course",
						"Description", 2L, "Category", false, CourseDifficulty.ADVANCED, "first", 91L),
				new CourseMetadataRow(8L, 4L, CourseStatus.PUBLISHED, true, 9L, "Course",
						"Description", 2L, "Category", false, CourseDifficulty.ADVANCED, "second", 91L)));

		var metadata = new CourseStructureQueryServiceImpl(courses, modules, lessons, contents)
				.getCourseMetadata(8L);

		assertThat(metadata).contains(new CourseMetadataSnapshot(
				8L, 4L, CourseStatus.PUBLISHED, true, 9L, "Course", "Description", 2L,
				"Category", false, CourseDifficulty.ADVANCED, List.of("first", "second"), 91L));
	}

	@Test
	void returnsEmptyMetadataWhenTheCourseDoesNotExist() {
		CourseRepository courses = mock(CourseRepository.class);
		when(courses.findCourseMetadataRows(404L)).thenReturn(List.of());

		assertThat(new CourseStructureQueryServiceImpl(
				courses, mock(ModuleRepository.class), mock(LessonRepository.class),
				mock(LessonContentRepository.class)).getCourseMetadata(404L)).isEmpty();
	}

	@Test
	void loadsAllLessonSummariesWithOneBatchedOutlineQuery() {
		CourseRepository courses = mock(CourseRepository.class);
		ModuleRepository modules = mock(ModuleRepository.class);
		LessonRepository lessons = mock(LessonRepository.class);
		LessonContentRepository contents = mock(LessonContentRepository.class);
		CourseEntity course = mock(CourseEntity.class);
		UserEntity owner = mock(UserEntity.class);
		ModuleEntity firstModule = mock(ModuleEntity.class);
		ModuleEntity secondModule = mock(ModuleEntity.class);
		when(courses.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(course));
		when(course.getId()).thenReturn(10L);
		when(course.getOwner()).thenReturn(owner);
		when(owner.getId()).thenReturn(3L);
		when(course.getTitle()).thenReturn("Batch loaded");
		when(course.getStatus()).thenReturn(CourseStatus.DRAFT);
		when(course.getVersion()).thenReturn(2L);
		when(firstModule.getId()).thenReturn(20L);
		when(firstModule.getTitle()).thenReturn("First");
		when(firstModule.getDescription()).thenReturn("First description");
		when(firstModule.getPosition()).thenReturn(0);
		when(secondModule.getId()).thenReturn(21L);
		when(secondModule.getTitle()).thenReturn("Second");
		when(secondModule.getDescription()).thenReturn("Second description");
		when(secondModule.getPosition()).thenReturn(1);
		when(modules.findAllByCourse_IdOrderByPositionAscIdAsc(10L))
				.thenReturn(List.of(firstModule, secondModule));
		when(lessons.findOutlineRowsByModuleIds(List.of(20L, 21L))).thenReturn(List.of(
				new LessonOutlineRow(30L, 20L, "Lesson A", null, 0, true, 100),
				new LessonOutlineRow(31L, 21L, "Lesson B", null, 0, false, 0)));
		CourseStructureQueryServiceImpl service =
				new CourseStructureQueryServiceImpl(courses, modules, lessons, contents);

		var structure = service.getStructure(10L);

		assertThat(structure.modules()).hasSize(2);
		assertThat(structure.modules().get(0).lessons()).containsExactly(
				new CourseStructureQueryService.LessonSnapshot(30L, "Lesson A", null, 0, 100, true));
		assertThat(structure.modules().get(1).lessons()).containsExactly(
				new CourseStructureQueryService.LessonSnapshot(31L, "Lesson B", null, 0, 0, false));
		assertThat(structure.modules().get(0).description()).isEqualTo("First description");
		verify(lessons).findOutlineRowsByModuleIds(List.of(20L, 21L));
		verify(contents, never()).findByLesson_Id(org.mockito.ArgumentMatchers.anyLong());
	}
}
