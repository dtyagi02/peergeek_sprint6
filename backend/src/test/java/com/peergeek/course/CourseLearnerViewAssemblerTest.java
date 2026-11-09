package com.peergeek.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peergeek.file.LessonResourceQueryService;
import com.peergeek.file.ResourceStatus;
import com.peergeek.file.ResourceType;
import org.junit.jupiter.api.Test;

class CourseLearnerViewAssemblerTest {
	@Test
	void assemblesViewWithoutApplyingPublicationOrAuthorizationPolicy() {
		CourseStructureQueryService structure = mock(CourseStructureQueryService.class);
		LessonResourceQueryService lessonResources = mock(LessonResourceQueryService.class);
		CourseLearnerViewAssembler assembler = new CourseLearnerViewAssembler(structure, lessonResources);
		ObjectNode document = JsonNodeFactory.instance.objectNode().put("type", "doc");
		CourseMetadataSnapshot metadata = new CourseMetadataSnapshot(
				4L, 9L, CourseStatus.DRAFT, false, 1, "Draft title", "Description",
				2L, "Category", true, CourseDifficulty.BEGINNER, List.of("topic"), null);
		CourseStructureQueryService.LessonSnapshot lesson =
				new CourseStructureQueryService.LessonSnapshot(7L, "Lesson", "Summary", 0, 1, true);
		CourseStructureQueryService.ModuleSnapshot module =
				new CourseStructureQueryService.ModuleSnapshot(6L, "Module", "Module description", 0,
						List.of(lesson));
		when(structure.getCourseMetadata(4)).thenReturn(Optional.of(metadata));
		when(structure.getStructure(4)).thenReturn(new CourseStructureQueryService.CourseStructure(
				new CourseStructureQueryService.CourseSummary(4L, 9L, "Draft title", "Description",
						CourseDifficulty.BEGINNER, CourseStatus.DRAFT, 1),
				List.of(module)));
		when(structure.getLessonContent(7)).thenReturn(
				new CourseStructureQueryService.LessonContentSnapshot("tiptap-json-v1", document, 1, 1, null));
		when(lessonResources.listResources(7)).thenReturn(List.of(
				new LessonResourceQueryService.ResourceSnapshot(10L, ResourceType.PDF, "Slides", "slides.pdf",
						"application/pdf", 120L, ResourceStatus.READY, null, null, 0, null),
				new LessonResourceQueryService.ResourceSnapshot(11L, ResourceType.PDF, "Pending", "pending.pdf",
						"application/pdf", 120L, ResourceStatus.PROCESSING, null, null, 1, null)));

		CourseLearnerView result = assembler.assemble(4);

		assertThat(result.title()).isEqualTo("Draft title");
		assertThat(result.modules()).hasSize(1);
		assertThat(result.modules().getFirst().lessons()).hasSize(1);
		assertThat(result.modules().getFirst().lessons().getFirst().resources())
				.extracting(CourseLearnerView.Resource::id).containsExactly(10L);
		verify(structure).getCourseMetadata(4);
	}
}
